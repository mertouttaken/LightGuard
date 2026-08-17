package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;

import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class LGPacketInjector {

    private final LightGuard plugin;
    private static final VarHandle KEEPALIVE_ID_FIELD;
    private static final VarHandle WINDOW_ID_FIELD;
    private static final VarHandle POS_X_FIELD, POS_Y_FIELD, POS_Z_FIELD, POS_FLAGS_FIELD;

    private static final Map<Class<?>, Double> PACKET_WEIGHTS = new HashMap<>();

    static {
        VarHandle kaId = null, wId = null, px = null, py = null, pz = null, pFlags = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(net.minecraft.server.v1_16_R3.PacketPlayOutKeepAlive.class, MethodHandles.lookup());
            for (Field f : net.minecraft.server.v1_16_R3.PacketPlayOutKeepAlive.class.getDeclaredFields()) {
                if (f.getType() == long.class) { f.setAccessible(true); kaId = lookup.unreflectVarHandle(f); break; }
            }

            MethodHandles.Lookup wLookup = MethodHandles.privateLookupIn(net.minecraft.server.v1_16_R3.PacketPlayOutOpenWindow.class, MethodHandles.lookup());
            for (Field f : net.minecraft.server.v1_16_R3.PacketPlayOutOpenWindow.class.getDeclaredFields()) {
                if (f.getType() == int.class) { f.setAccessible(true); wId = wLookup.unreflectVarHandle(f); break; }
            }

            MethodHandles.Lookup pLookup = MethodHandles.privateLookupIn(net.minecraft.server.v1_16_R3.PacketPlayOutPosition.class, MethodHandles.lookup());
            int doubleCount = 0;
            for (Field f : net.minecraft.server.v1_16_R3.PacketPlayOutPosition.class.getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == double.class) {
                    if (doubleCount == 0) px = pLookup.unreflectVarHandle(f);
                    else if (doubleCount == 1) py = pLookup.unreflectVarHandle(f);
                    else if (doubleCount == 2) pz = pLookup.unreflectVarHandle(f);
                    doubleCount++;
                } else if (f.getType() == java.util.Set.class) {
                    pFlags = pLookup.unreflectVarHandle(f);
                }
            }

            PACKET_WEIGHTS.put(net.minecraft.server.v1_16_R3.PacketPlayInWindowClick.class, 15.0);
            PACKET_WEIGHTS.put(net.minecraft.server.v1_16_R3.PacketPlayInSetCreativeSlot.class, 30.0);
            PACKET_WEIGHTS.put(net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload.class, 25.0);
            PACKET_WEIGHTS.put(net.minecraft.server.v1_16_R3.PacketPlayInUpdateSign.class, 30.0);
            PACKET_WEIGHTS.put(net.minecraft.server.v1_16_R3.PacketPlayInUseItem.class, 5.0);
            PACKET_WEIGHTS.put(net.minecraft.server.v1_16_R3.PacketPlayInBlockPlace.class, 10.0);

            PACKET_WEIGHTS.put(net.minecraft.server.v1_16_R3.PacketPlayInFlying.class, 1.0);
            PACKET_WEIGHTS.put(net.minecraft.server.v1_16_R3.PacketPlayInFlying.PacketPlayInPosition.class, 1.0);
            PACKET_WEIGHTS.put(net.minecraft.server.v1_16_R3.PacketPlayInFlying.PacketPlayInPositionLook.class, 1.0);
            PACKET_WEIGHTS.put(net.minecraft.server.v1_16_R3.PacketPlayInFlying.PacketPlayInLook.class, 1.0);

        } catch (Exception ignored) {}

        KEEPALIVE_ID_FIELD = kaId; WINDOW_ID_FIELD = wId;
        POS_X_FIELD = px; POS_Y_FIELD = py; POS_Z_FIELD = pz; POS_FLAGS_FIELD = pFlags;
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

                    double weight = PACKET_WEIGHTS.getOrDefault(msg.getClass(), 2.5);

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

                    super.channelRead(ctx, msg);
                }

                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    try {
                        if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayOutOpenWindow && WINDOW_ID_FIELD != null) {
                            int windowId = (int) WINDOW_ID_FIELD.get(msg);
                            protocolPlayer.setInventoryState(true, windowId);

                        } else if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayOutPosition && POS_X_FIELD != null) {
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

                        } else if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayOutKeepAlive && KEEPALIVE_ID_FIELD != null) {
                            long id = (long) KEEPALIVE_ID_FIELD.get(msg);
                            protocolPlayer.getKeepAliveTracker().onKeepAliveSent(id);
                        }
                    } catch (Throwable ignored) {}

                    super.write(ctx, msg, promise);
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
            if (channel != null && channel.pipeline().get("lg_check") != null) {
                channel.pipeline().remove("lg_check");
            }
        } catch (Exception ignored) { }
    }

    private void releaseMessage(Object msg) {
        try {
            if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload) {
                net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload p = (net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload) msg;
                if (p.data != null) {
                    ReferenceCountUtil.safeRelease(p.data);
                }
            } else {
                ReferenceCountUtil.safeRelease(msg);
            }
        } catch (Exception ignored) {}
    }
}