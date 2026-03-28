package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;
import com.mertout.lightguard.netty.nbt.NBTStreamingScanner;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
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
            channel.pipeline().addBefore("decoder", "lg_raw", new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    if (msg instanceof ByteBuf) {
                        ByteBuf buf = (ByteBuf) msg;
                        int capacity = buf.readableBytes();

                        if (capacity > MAX_RAW_CAPACITY) {
                            plugin.getLogger().warning("§c[LightGuard] Dropped oversized raw packet from " + player.getName());
                            ctx.close();
                            return;
                        }

                        buf.markReaderIndex();
                        try {
                            int packetId = readVarInt(buf);

                            if (packetId == 0x28) {
                                buf.skipBytes(2);
                                if (buf.readBoolean()) {
                                    readVarInt(buf);
                                    buf.readByte();
                                    if (buf.isReadable() && buf.getByte(buf.readerIndex()) == 10) {
                                        if (!NBTStreamingScanner.scan(buf)) {
                                            plugin.getLogger().warning("§c[LightGuard] NBT Bomb Blocked (Creative) from " + player.getName());
                                            ctx.close();
                                            return;
                                        }
                                    }
                                }
                            }
                            else if (packetId == 0x0B) {
                                int channelLen = readVarInt(buf);
                                if (channelLen > 0 && channelLen <= 128 && buf.readableBytes() >= channelLen) {
                                    byte[] channelBytes = new byte[channelLen];
                                    buf.readBytes(channelBytes);
                                    String channelName = new String(channelBytes, StandardCharsets.UTF_8);

                                    if (channelName.contains("book") || channelName.contains("bedit") || channelName.contains("bsign")) {
                                        if (buf.isReadable() && buf.getByte(buf.readerIndex()) == 10) {
                                            if (!NBTStreamingScanner.scan(buf)) {
                                                plugin.getLogger().warning("§c[LightGuard] Malicious Book Blocked (Payload) from " + player.getName());
                                                ctx.close();
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {
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
                        ctx.close();
                        return;
                    }

                    protocolPlayer.incrementPacketCount();
                    if (!plugin.getDispatcher().dispatch(msg, protocolPlayer)) {
                        if (msg instanceof net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload) {
                            net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload p = (net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload) msg;
                            if (p.data != null && p.data.refCnt() > 0) p.data.release();
                        }
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
        switch (name) {
            case "PacketPlayInWindowClick": return 50.0;
            case "PacketPlayInSetCreativeSlot": return 80.0;
            case "PacketPlayInCustomPayload": return 40.0;
            case "PacketPlayInUseItem": return 30.0;
            case "PacketPlayInBlockDig": return 20.0;
            case "PacketPlayInFlying":
            case "PacketPlayInPosition":
            case "PacketPlayInPositionLook":
            case "PacketPlayInLook": return 1.0;
            default: return 5.0;
        }
    }
}