package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;
import com.mertout.lightguard.netty.nbt.NBTStreamingScanner;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
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

                            // 0x28 -> Creative Slot
                            if (packetId == 0x28) {
                                buf.skipBytes(2); // Slot
                                if (buf.readBoolean()) { // Present
                                    readVarInt(buf); // Item ID
                                    buf.readByte(); // Count
                                    // TagID 10 = NBT Compound
                                    if (buf.isReadable() && buf.getByte(buf.readerIndex()) == 10) {
                                        if (!NBTStreamingScanner.scan(buf)) {
                                            plugin.getLogger().warning("§c[LightGuard] NBT Bomb Blocked (Creative) from " + player.getName());
                                            releaseMessage(msg);
                                            ctx.close();
                                            return;
                                        }
                                    }
                                }
                            }
                            // 0x0B -> Custom Payload
                            else if (packetId == 0x0B) {
                                int channelLen = readVarInt(buf);
                                if (channelLen > 0 && channelLen <= 128 && buf.readableBytes() >= channelLen) {
                                    byte[] channelBytes = new byte[channelLen];
                                    buf.readBytes(channelBytes);
                                    String channelName = new String(channelBytes, StandardCharsets.UTF_8);

                                    if (channelName.contains("book") || channelName.contains("bedit") || channelName.contains("bsign")) {
                                        if (buf.isReadable() && buf.getByte(buf.readerIndex()) == 10) {
                                            if (!NBTStreamingScanner.scan(buf)) {
                                                plugin.getLogger().warning("§c[LightGuard] Malicious Book Blocked from " + player.getName());
                                                releaseMessage(msg);
                                                ctx.close();
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {
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
                        plugin.getLogger().warning("§c[LightGuard] Rate Limit Exceeded: " + player.getName());
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

    /**
     * 💧 CİLA: Bellek Sızıntısı Koruması (Zero-Leak)
     * Paketi düşürdüğümüzde içindeki ByteBuf verisini Netty'ye iade eder.
     */
    private void releaseMessage(Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf b = (ByteBuf) msg;
            if (b.refCnt() > 0) b.release();
        } else if (msg instanceof ByteBufHolder) {
            ((ByteBufHolder) msg).content().release();
        } else if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload) {
            net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload p = (net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload) msg;
            if (p.data != null && p.data.refCnt() > 0) p.data.release();
        }
    }

    private int readVarInt(ByteBuf buf) {
        int numRead = 0, result = 0;
        byte read;
        do {
            read = buf.readByte();
            result |= ((read & 0b01111111) << (7 * numRead++));
            if (numRead > 5) throw new RuntimeException("VarInt too big");
        } while ((read & 0b10000000) != 0);
        return result;
    }

    private double getPacketWeight(Object packet) {
        String name = packet.getClass().getSimpleName();
        return switch (name) {
            case "PacketPlayInWindowClick" -> 50.0;
            case "PacketPlayInSetCreativeSlot" -> 80.0;
            case "PacketPlayInCustomPayload" -> 40.0;
            case "PacketPlayInUseItem" -> 30.0;
            case "PacketPlayInBlockDig" -> 20.0;
            case "PacketPlayInFlying", "PacketPlayInPosition", "PacketPlayInPositionLook", "PacketPlayInLook" -> 1.0;
            default -> 5.0;
        };
    }
}