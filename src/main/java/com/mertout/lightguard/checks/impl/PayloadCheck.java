package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PayloadCheck extends Check {

    private static final int MAX_CHANNELS_PER_PLAYER = 124;
    private final List<String> blockedChannels;
    private final int maxPayloadSize;
    private final boolean brandCheckEnabled;
    private final int maxBrandLength;
    private final boolean blockInvalidBrandChars;

    public PayloadCheck(PlayerData data) {
        super(data, "Payload", "payload");
        this.blockedChannels = plugin.getConfig().getStringList("checks.payload.blocked-channels");
        this.maxPayloadSize = plugin.getConfig().getInt("checks.payload.max-size", 2048);
        this.brandCheckEnabled = plugin.getConfig().getBoolean("checks.client-brand.enabled");
        this.maxBrandLength = plugin.getConfig().getInt("checks.client-brand.max-length", 20);
        this.blockInvalidBrandChars = plugin.getConfig().getBoolean("checks.client-brand.block-invalid-chars");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInCustomPayload) {
            PacketPlayInCustomPayload p = (PacketPlayInCustomPayload) packet;
            String packetName = "PacketPlayInCustomPayload";
            String channel = p.tag.toString();

            for (String blocked : blockedChannels) {
                if (channel.equalsIgnoreCase(blocked) || channel.toLowerCase().contains(blocked.toLowerCase())) {
                    flag("Blocked Channel: " + channel, packetName);
                    return false;
                }
            }

            if (channel.equals("minecraft:register") || channel.equals("REGISTER")) {
                ByteBuf data = p.data;
                int readableBytes = data.readableBytes();
                int channelCount = 0;

                for (int i = 0; i < readableBytes; i++) {
                    if (data.getByte(data.readerIndex() + i) == 0) channelCount++;
                }
                if (channelCount > 20) {
                    flag("Channel Register Flood", packetName);
                    return false;
                }

                long now = System.currentTimeMillis();
                if (now - this.data.getLastChannelRegister().get() > 60000) {
                    this.data.getRecentChannelRegisters().set(0);
                    this.data.getLastChannelRegister().set(now);
                }
                if (this.data.getRecentChannelRegisters().addAndGet(channelCount + 1) > 50) {
                    flag("Channel Register Flood (Rate Limit)", packetName);
                    return false;
                }

                Set<String> channels = this.data.getRegisteredChannels();
                if (channels.size() + channelCount >= MAX_CHANNELS_PER_PLAYER) {
                    flag("Max Channel Limit Reached", packetName);
                    return false;
                }

                if (channelCount > 0) {
                    int start = data.readerIndex();
                    int pos = start;
                    for (int i = 0; i <= readableBytes; i++) {
                        if (i == readableBytes || data.getByte(start + i) == 0) {
                            if (i > pos) {
                                int length = i - pos;
                                String ch = data.toString(pos, length, StandardCharsets.UTF_8);
                                if (!ch.isEmpty() && !channels.contains(ch)) {
                                    channels.add(ch);
                                }
                            }
                            pos = i + 1;
                        }
                    }
                }
            }

            try {
                if (p.data.readableBytes() > maxPayloadSize && !channel.equals("WDL|INIT")) {
                    flag("Oversized Payload (" + p.data.readableBytes() + " bytes)", packetName);
                    return false;
                }
            } catch (Exception ignored) { return false; }

            if (channel.equals("minecraft:brand") || channel.equals("MC|Brand")) {
                if (brandCheckEnabled) {
                    ByteBuf copy = p.data.copy();
                    try {
                        String brand = readString(copy);
                        if (brand.length() > maxBrandLength) {
                            flag("Oversized Client Brand", packetName);
                            return false;
                        }
                        if (blockInvalidBrandChars && !brand.matches("[a-zA-Z0-9_ .-]+")) {
                            flag("Invalid Brand Characters", packetName);
                            return false;
                        }
                    } finally { copy.release(); }
                }
            }
        }
        return true;
    }

    private String readString(ByteBuf buf) {
        try {
            int len = readVarInt(buf);
            if (len > 32767 || buf.readableBytes() < len) return "";
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    private int readVarInt(ByteBuf buf) {
        int numRead = 0; int result = 0; byte read;
        do {
            read = buf.readByte();
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new RuntimeException("VarInt is too big");
        } while ((read & 0x80) != 0);
        return result;
    }
}