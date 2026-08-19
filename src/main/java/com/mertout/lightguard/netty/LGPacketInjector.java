package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;

import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.bukkit.entity.Player;

import org.bukkit.craftbukkit.entity.CraftPlayer;

import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

import net.minecraft.world.entity.Relative;

import java.util.HashMap;
import java.util.Map;

public class LGPacketInjector {

    private final LightGuard plugin;

    private static final Map<Class<?>, Double> PACKET_WEIGHTS = new HashMap<>();

    static {
        PACKET_WEIGHTS.put(ServerboundContainerClickPacket.class, 15.0);
        PACKET_WEIGHTS.put(ServerboundSetCreativeModeSlotPacket.class, 30.0);
        PACKET_WEIGHTS.put(ServerboundCustomPayloadPacket.class, 25.0);
        PACKET_WEIGHTS.put(ServerboundSignUpdatePacket.class, 30.0);
        PACKET_WEIGHTS.put(ServerboundUseItemPacket.class, 5.0);
        PACKET_WEIGHTS.put(ServerboundUseItemOnPacket.class, 10.0);

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

        if (channel.pipeline().get("lg_prelogin_raw") != null) {
            channel.pipeline().remove("lg_prelogin_raw");
        }

        if (channel.pipeline().get("lg_check") == null) {
            ChannelDuplexHandler checkHandler = new ChannelDuplexHandler() {

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

                    if (msg instanceof ServerboundContainerClosePacket) {
                        protocolPlayer.setInventoryState(false, -1);
                    } else if (msg instanceof ServerboundContainerClickPacket) {
                        ServerboundContainerClickPacket clickPacket = (ServerboundContainerClickPacket) msg;

                        if (clickPacket.getContainerId() == 0) {
                            protocolPlayer.setInventoryState(true, 0);
                        }
                    }

                    double weight = PACKET_WEIGHTS.getOrDefault(msg.getClass(), 2.5);

                    if (protocolPlayer.getRateLimiter().addAndCheck(weight)) {
                        plugin.getLogger().warning("Rate Limit Exceeded: " + player.getName());
                        plugin.getActionQueue().pushKick(player, "Excessive Packet Sending (Rate Limit)");
                        ReferenceCountUtil.safeRelease(msg);
                        return;
                    }

                    protocolPlayer.incrementPacketCount();

                    try {
                        if (!plugin.getDispatcher().dispatch(msg, protocolPlayer)) {
                            ReferenceCountUtil.safeRelease(msg);
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
                        if (msg instanceof ClientboundOpenScreenPacket) {
                            ClientboundOpenScreenPacket openScreen = (ClientboundOpenScreenPacket) msg;
                            protocolPlayer.setInventoryState(true, openScreen.getContainerId());

                        } else if (msg instanceof ClientboundPlayerPositionPacket) {
                            ClientboundPlayerPositionPacket posPacket = (ClientboundPlayerPositionPacket) msg;

                            boolean hasRelativeCoord = posPacket.relatives().contains(Relative.X) ||
                                    posPacket.relatives().contains(Relative.Y) ||
                                    posPacket.relatives().contains(Relative.Z);

                            if (!hasRelativeCoord) {
                                protocolPlayer.setLastLocation(
                                        posPacket.change().position().x,
                                        posPacket.change().position().y,
                                        posPacket.change().position().z
                                );
                            }
                            protocolPlayer.markTeleport();

                        } else if (msg instanceof ClientboundKeepAlivePacket) {
                            ClientboundKeepAlivePacket keepAlive = (ClientboundKeepAlivePacket) msg;
                            protocolPlayer.getKeepAliveTracker().onKeepAliveSent(keepAlive.getId());
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
            Channel channel = ((CraftPlayer) player).getHandle().connection.connection.channel;
            if (channel != null && channel.pipeline().get("lg_check") != null) {
                channel.pipeline().remove("lg_check");
            }
        } catch (Exception ignored) { }
    }
}