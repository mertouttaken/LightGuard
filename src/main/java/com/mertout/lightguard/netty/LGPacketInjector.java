package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;

import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class LGPacketInjector {

    private final LightGuard plugin;
    private static final VarHandle KEEPALIVE_ID_FIELD;
    private static final VarHandle WINDOW_ID_FIELD;
    private static final VarHandle POS_X_FIELD, POS_Y_FIELD, POS_Z_FIELD, POS_FLAGS_FIELD;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            KEEPALIVE_ID_FIELD = MethodHandles.privateLookupIn(net.minecraft.server.v1_16_R3.PacketPlayOutKeepAlive.class, MethodHandles.lookup())
                    .findVarHandle(net.minecraft.server.v1_16_R3.PacketPlayOutKeepAlive.class, "a", long.class);
            WINDOW_ID_FIELD = MethodHandles.privateLookupIn(net.minecraft.server.v1_16_R3.PacketPlayOutOpenWindow.class, lookup)
                    .findVarHandle(net.minecraft.server.v1_16_R3.PacketPlayOutOpenWindow.class, "a", int.class);
            POS_X_FIELD = MethodHandles.privateLookupIn(net.minecraft.server.v1_16_R3.PacketPlayOutPosition.class, lookup)
                    .findVarHandle(net.minecraft.server.v1_16_R3.PacketPlayOutPosition.class, "a", double.class);
            POS_Y_FIELD = MethodHandles.privateLookupIn(net.minecraft.server.v1_16_R3.PacketPlayOutPosition.class, lookup)
                    .findVarHandle(net.minecraft.server.v1_16_R3.PacketPlayOutPosition.class, "b", double.class);
            POS_Z_FIELD = MethodHandles.privateLookupIn(net.minecraft.server.v1_16_R3.PacketPlayOutPosition.class, lookup)
                    .findVarHandle(net.minecraft.server.v1_16_R3.PacketPlayOutPosition.class, "c", double.class);
            POS_FLAGS_FIELD = MethodHandles.privateLookupIn(net.minecraft.server.v1_16_R3.PacketPlayOutPosition.class, lookup)
                    .findVarHandle(net.minecraft.server.v1_16_R3.PacketPlayOutPosition.class, "f", java.util.Set.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public LGPacketInjector(LightGuard plugin) {
        this.plugin = plugin;
    }

    public void injectPlayer(Player player, final ProtocolPlayer protocolPlayer) {
        Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;

        if (channel.pipeline().get("lg_prelogin_raw") != null) {
            channel.pipeline().remove("lg_prelogin_raw");
        }

        if (channel.pipeline().get("lg_check") == null) {
            ChannelDuplexHandler checkHandler = new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

                    if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayInCloseWindow) {
                        protocolPlayer.setInventoryState(false, -1);
                    } else if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayInWindowClick) {
                        net.minecraft.server.v1_16_R3.PacketPlayInWindowClick clickPacket =
                                (net.minecraft.server.v1_16_R3.PacketPlayInWindowClick) msg;

                        if (clickPacket.b() == 0) {
                            protocolPlayer.setInventoryState(true, 0);
                        }
                    }

                    double weight = getPacketWeight(msg);
                    if (protocolPlayer.getRateLimiter().addAndCheck(weight)) {
                        plugin.getLogger().warning("Rate Limit Exceeded: " + player.getName());
                        plugin.getActionQueue().pushKick(player, "Excessive Packet Sending (Rate Limit)");
                        releaseMessage(msg);
                        return;
                    }

                    protocolPlayer.incrementPacketCount();

                    try {
                        if (!plugin.getDispatcher().dispatch(msg, protocolPlayer)) {
                            releaseMessage(msg);
                            return;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Dispatcher Error for " + player.getName() + ": " + e.getMessage());
                    }
                    plugin.getLogger().warning("Rate Limit Exceeded: " + player.getName());
                    plugin.getActionQueue().pushKick(player, "Excessive Packet Sending (Rate Limit)");
                    super.channelRead(ctx, msg);
                }

                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

                    if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayOutOpenWindow) {
                        try {
                            int windowId = (int) WINDOW_ID_FIELD.get(msg);
                            protocolPlayer.setInventoryState(true, windowId);
                        } catch (Exception ignored) {}
                    } else if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayOutPosition) {
                        try {
                            java.util.Set<?> flags = (java.util.Set<?>) POS_FLAGS_FIELD.get(msg);
                            boolean hasRelativeCoord = false;

                            if (flags != null) {
                                for (Object f : flags) {
                                    String name = f.toString();
                                    if (name.equals("X") || name.equals("Y") || name.equals("Z")) {
                                        hasRelativeCoord = true;
                                        break;
                                    }
                                }
                            }

                            if (!hasRelativeCoord) {
                                double safeX = (double) POS_X_FIELD.get(msg);
                                double safeY = (double) POS_Y_FIELD.get(msg);
                                double safeZ = (double) POS_Z_FIELD.get(msg);
                                protocolPlayer.setLastLocation(safeX, safeY, safeZ);
                            }
                            protocolPlayer.markTeleport();
                        } catch (Exception ignored) {}
                    } else if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayOutKeepAlive) {
                        try {
                            long id = (long) KEEPALIVE_ID_FIELD.get(msg);
                            protocolPlayer.getKeepAliveTracker().onKeepAliveSent(id);
                        } catch (Exception ignored) {}
                    }

                    super.write(ctx, msg, promise);
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    plugin.getLogger().severe("Netty Pipeline Exception for " + player.getName() + " -> " + cause.getMessage());
                    super.exceptionCaught(ctx, cause);
                }
            };

            if (channel.pipeline().get("decoder") != null) {
                channel.pipeline().addAfter("decoder", "lg_check", checkHandler);
            } else {
                channel.pipeline().addLast("lg_check", checkHandler);
            }
        }
    }

    public void removePlayer(Player player) {
        try {
            Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
            if (channel != null) {
                if (channel.pipeline().get("lg_check") != null) channel.pipeline().remove("lg_check");
            }
        } catch (Exception ignored) { }
    }

    private void releaseMessage(Object msg) {
        try {
            if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload) {
                net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload p =
                        (net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload) msg;
                if (p.data != null) {
                    ReferenceCountUtil.safeRelease(p.data);
                }
            } else {
                ReferenceCountUtil.safeRelease(msg);
            }
        } catch (Exception ignored) {}
    }

    private double getPacketWeight(Object packet) {
        String name = packet.getClass().getSimpleName();
        switch (name) {
            case "PacketPlayInWindowClick": return 40.0;
            case "PacketPlayInSetCreativeSlot": return 80.0;
            case "PacketPlayInCustomPayload": return 50.0;
            case "PacketPlayInUpdateSign": return 60.0;
            case "PacketPlayInUseItem":
            case "PacketPlayInBlockPlace": return 25.0;
            case "PacketPlayInFlying":
            case "PacketPlayInPosition":
            case "PacketPlayInPositionLook":
            case "PacketPlayInLook": return 1.0;
            default: return 5.0;
        }
    }
}