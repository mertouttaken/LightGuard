package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LGPacketInjector {

    private final LightGuard plugin;
    private static final Map<Class<?>, Double> PACKET_WEIGHTS = new HashMap<>();

    static {
        PACKET_WEIGHTS.put(ServerboundContainerClickPacket.class, 8.0);
        PACKET_WEIGHTS.put(ServerboundSetCreativeModeSlotPacket.class, 10.0);
        PACKET_WEIGHTS.put(ServerboundCustomPayloadPacket.class, 25.0);
        PACKET_WEIGHTS.put(ServerboundSignUpdatePacket.class, 30.0);
        PACKET_WEIGHTS.put(ServerboundUseItemPacket.class, 5.0);
        PACKET_WEIGHTS.put(ServerboundUseItemOnPacket.class, 8.0);

        PACKET_WEIGHTS.put(ServerboundMovePlayerPacket.class, 1.0);
        PACKET_WEIGHTS.put(ServerboundMovePlayerPacket.Pos.class, 1.0);
        PACKET_WEIGHTS.put(ServerboundMovePlayerPacket.PosRot.class, 1.0);
        PACKET_WEIGHTS.put(ServerboundMovePlayerPacket.Rot.class, 1.0);
        PACKET_WEIGHTS.put(ServerboundMovePlayerPacket.StatusOnly.class, 1.0);
    }

    public LGPacketInjector(LightGuard plugin) {
        this.plugin = plugin;
    }

    public void injectPlayer(Player player, final ProtocolPlayer protocolPlayer) {
        Channel channel = ((CraftPlayer) player).getHandle().connection.connection.channel;
        if (channel == null) return;

        channel.eventLoop().execute(() -> {
            ChannelPipeline pipeline = channel.pipeline();

            if (pipeline.get("lg_prelogin_raw") != null) {
                pipeline.remove("lg_prelogin_raw");
            }

            if (pipeline.get("lg_check") == null) {
                ChannelDuplexHandler checkHandler = new ChannelDuplexHandler() {

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof ServerboundContainerClosePacket) {
                            protocolPlayer.setInventoryState(false, -1);
                        } else if (msg instanceof ServerboundContainerClickPacket clickPacket) {
                            if (clickPacket.getContainerId() == 0) {
                                protocolPlayer.setInventoryState(true, 0);
                            }
                        }

                        double weight = PACKET_WEIGHTS.getOrDefault(msg.getClass(), 2.0);

                        if (protocolPlayer.getRateLimiter().addAndCheck(weight)) {
                            plugin.getLogger().warning("Rate Limit Exceeded: " + player.getName());
                            plugin.getActionQueue().pushKick(player, "Excessive Packet Sending (Rate Limit)");
                            releasePacketBuffers(msg);
                            return;
                        }

                        protocolPlayer.incrementPacketCount();

                        try {
                            if (!plugin.getDispatcher().dispatch(msg, protocolPlayer)) {
                                releasePacketBuffers(msg);
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
                            if (msg instanceof ClientboundBundlePacket bundlePacket) {
                                for (Packet<?> subPacket : bundlePacket.subPackets()) {
                                    handleOutboundPacket(subPacket, protocolPlayer);
                                }
                            } else {
                                handleOutboundPacket(msg, protocolPlayer);
                            }
                        } catch (Throwable ignored) {}

                        super.write(ctx, msg, promise);
                    }
                };

                if (pipeline.get("decoder") != null) {
                    pipeline.addAfter("decoder", "lg_check", checkHandler);
                } else {
                    pipeline.addLast("lg_check", checkHandler);
                }
            }
        });
    }

    private void handleOutboundPacket(Object msg, ProtocolPlayer protocolPlayer) {
        if (msg instanceof ClientboundOpenScreenPacket openScreen) {
            protocolPlayer.setInventoryState(true, openScreen.getContainerId());
        } else if (msg instanceof ClientboundPlayerPositionPacket posPacket) {
            Vec3 pos = posPacket.change().position();
            Set<Relative> relatives = posPacket.relatives();

            double baseX = protocolPlayer.getLastX();
            double baseY = protocolPlayer.getLastY();
            double baseZ = protocolPlayer.getLastZ();

            double targetX = relatives.contains(Relative.X) ? (baseX != -1 ? baseX + pos.x() : pos.x()) : pos.x();
            double targetY = relatives.contains(Relative.Y) ? (baseY != -1 ? baseY + pos.y() : pos.y()) : pos.y();
            double targetZ = relatives.contains(Relative.Z) ? (baseZ != -1 ? baseZ + pos.z() : pos.z()) : pos.z();

            protocolPlayer.setLastLocation(targetX, targetY, targetZ);
            protocolPlayer.markTeleport();
        } else if (msg instanceof ClientboundKeepAlivePacket keepAlive) {
            protocolPlayer.getKeepAliveTracker().onKeepAliveSent(keepAlive.getId());
        }
    }

    private void releasePacketBuffers(Object msg) {
        if (msg instanceof ServerboundCustomPayloadPacket payloadPacket) {
            ReferenceCountUtil.safeRelease(payloadPacket.payload());
        }
        ReferenceCountUtil.safeRelease(msg);
    }

    public void removePlayer(Player player) {
        try {
            Channel channel = ((CraftPlayer) player).getHandle().connection.connection.channel;
            if (channel != null && channel.isOpen()) {
                channel.eventLoop().execute(() -> {
                    if (channel.pipeline().get("lg_check") != null) {
                        channel.pipeline().remove("lg_check");
                    }
                });
            }
        } catch (Exception ignored) {}
    }
}