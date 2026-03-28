package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;
import com.mertout.lightguard.netty.nbt.NBTStreamingScanner;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.*;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;

public class LGPacketInjector {

    private final LightGuard plugin;
    private static final int MAX_RAW_CAPACITY = 40960;

    public LGPacketInjector(LightGuard plugin) {
        this.plugin = plugin;
    }

    public void injectPlayer(Player player, final ProtocolPlayer protocolPlayer) {
        Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;

        if (channel.pipeline().get("lg_raw") == null) {
            channel.pipeline().addBefore("decoder", "lg_raw", new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (msg instanceof ByteBuf) {
                        ByteBuf buf = (ByteBuf) msg;

                        if (buf.readableBytes() > MAX_RAW_CAPACITY) {
                            plugin.getLogger().warning("§c[LightGuard] Dropped oversized raw packet from " + player.getName());
                            releaseMessage(msg);
                            ctx.close();
                            return;
                        }

                        buf.markReaderIndex();
                        try {
                            int packetId = readVarInt(buf);

                            // 0x28: Creative Slot | 0x0B: Custom Payload
                            if (packetId == 0x28 || packetId == 0x0B) {
                                if (packetId == 0x28) {
                                    buf.skipBytes(2); // Slot skip
                                    if (buf.readBoolean()) { // Presence check
                                        readVarInt(buf); // Item ID
                                        buf.readByte(); // Count
                                        if (buf.isReadable() && buf.getByte(buf.readerIndex()) == 10) {
                                            if (!NBTStreamingScanner.scan(buf)) {
                                                plugin.getLogger().warning("§c[LightGuard] NBT Bomb Blocked from " + player.getName());
                                                releaseMessage(msg); ctx.close(); return;
                                            }
                                        }
                                    }
                                } else { // 0x0B
                                    int channelLen = readVarInt(buf);
                                    if (channelLen > 0 && channelLen <= 128 && buf.readableBytes() >= channelLen) {
                                        buf.skipBytes(channelLen);
                                        if (buf.isReadable() && buf.getByte(buf.readerIndex()) == 10) {
                                            if (!NBTStreamingScanner.scan(buf)) {
                                                plugin.getLogger().warning("§c[LightGuard] Malicious Payload NBT from " + player.getName());
                                                releaseMessage(msg); ctx.close(); return;
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            releaseMessage(msg);
                            ctx.close();
                            return;
                        } finally {
                            buf.resetReaderIndex();
                        }
                    }
                    super.channelRead(ctx, msg);
                }
            });
        }

        if (channel.pipeline().get("lg_check") == null) {
            channel.pipeline().addBefore("packet_handler", "lg_check", new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    double weight = getPacketWeight(msg);

                    if (protocolPlayer.getRateLimiter().addAndCheck(weight)) {
                        plugin.getLogger().warning("§c[LightGuard] Rate Limit: " + player.getName());
                        releaseMessage(msg);
                        ctx.close();
                        return;
                    }

                    protocolPlayer.incrementPacketCount();
                    if (!plugin.getDispatcher().dispatch(msg, protocolPlayer)) {
                        releaseMessage(msg);
                        return;
                    }

                    super.channelRead(ctx, msg);
                }
            });
        }
    }

    public void removePlayer(Player player) {
        try {
            Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
            if (channel != null) {
                if (channel.pipeline().get("lg_raw") != null) channel.pipeline().remove("lg_raw");
                if (channel.pipeline().get("lg_check") != null) channel.pipeline().remove("lg_check");
            }
        } catch (Exception ignored) { }
    }

    private void releaseMessage(Object msg) {
        try {
            if (msg instanceof ByteBuf) {
                ByteBuf b = (ByteBuf) msg;
                if (b.refCnt() > 0) b.release();
            } else if (msg instanceof ByteBufHolder) {
                ByteBuf b = ((ByteBufHolder) msg).content();
                if (b.refCnt() > 0) b.release();
            } else if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload) {
                net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload p = (net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload) msg;
                if (p.data != null && p.data.refCnt() > 0) p.data.release();
            }
        } catch (Exception ignored) {}
    }

    private int readVarInt(ByteBuf buf) {
        int numRead = 0, result = 0;
        byte read;
        do {
            if (numRead >= 5) return -1;
            read = buf.readByte();
            result |= ((read & 0b01111111) << (7 * numRead++));
        } while ((read & 0b10000000) != 0);
        return result;
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