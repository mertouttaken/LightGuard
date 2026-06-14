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

    static {
        try {
            KEEPALIVE_ID_FIELD = MethodHandles.privateLookupIn(net.minecraft.server.v1_16_R3.PacketPlayOutKeepAlive.class, MethodHandles.lookup())
                    .findVarHandle(net.minecraft.server.v1_16_R3.PacketPlayOutKeepAlive.class, "a", long.class);
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
            channel.pipeline().addBefore("packet_handler", "lg_check", new ChannelDuplexHandler() {

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

                    if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayInCloseWindow) {
                        protocolPlayer.setInventoryState(false, -1);
                    }
                    else if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayInWindowClick) {
                        net.minecraft.server.v1_16_R3.PacketPlayInWindowClick clickPacket =
                                (net.minecraft.server.v1_16_R3.PacketPlayInWindowClick) msg;

                        if (clickPacket.b() == 0) {
                            protocolPlayer.setInventoryState(true, 0);
                        }
                    }

                    double weight = getPacketWeight(msg);
                    if (protocolPlayer.getRateLimiter().addAndCheck(weight)) {
                        plugin.getLogger().warning("§c[LightGuard] Rate Limit: " + player.getName());
                        releaseMessage(msg);
                        ctx.close();
                        return;
                    }

                    protocolPlayer.incrementPacketCount();

                    try {
                        if (!plugin.getDispatcher().dispatch(msg, protocolPlayer)) {
                            releaseMessage(msg);
                            return;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("[LightGuard] Dispatcher Error for " + player.getName() + ": " + e.getMessage());
                    }

                    super.channelRead(ctx, msg);
                }

                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayOutKeepAlive) {
                        try {
                            long id = (long) KEEPALIVE_ID_FIELD.get(msg);
                            protocolPlayer.getKeepAliveTracker().onKeepAliveSent(id);
                        } catch (Exception ignored) {}
                    }
                    super.write(ctx, msg, promise);
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    plugin.getLogger().severe("[LightGuard] Netty Pipeline Exception for " + player.getName() + " -> " + cause.getMessage());
                    super.exceptionCaught(ctx, cause);
                }
            });
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
        return switch (name) {
            case "PacketPlayInWindowClick" -> 40.0;
            case "PacketPlayInSetCreativeSlot" -> 80.0;
            case "PacketPlayInCustomPayload" -> 50.0;
            case "PacketPlayInUpdateSign" -> 60.0;
            case "PacketPlayInUseItem", "PacketPlayInBlockPlace" -> 25.0;
            case "PacketPlayInFlying", "PacketPlayInPosition", "PacketPlayInPositionLook", "PacketPlayInLook" -> 1.0;
            default -> 5.0;
        };
    }
}