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
                ByteBuf dataCopy = p.data.copy();
                try {
                    String content = dataCopy.toString(StandardCharsets.UTF_8);
                    String[] reqChannels = content.split("\0");
                    if (reqChannels.length > 20) {
                        flag("Channel Register Flood", packetName);
                        return false;
                    }

                    Set<String> channels = data.getRegisteredChannels();
                    for (String ch : reqChannels) {
                        if (ch.isEmpty()) continue;
                        if (!channels.contains(ch)) {
                            if (channels.size() >= MAX_CHANNELS_PER_PLAYER) {
                                flag("Max Channel Limit Reached", packetName);
                                return false;
                            }
                            channels.add(ch);
                        }
                    }
                } catch (Exception e) { return false; } finally { dataCopy.release(); }
            }

            try {
                if (p.data.readableBytes() > maxPayloadSize && !channel.equals("WDL|INIT")) {
                    flag("Oversized Payload (" + p.data.readableBytes() + " bytes)", packetName);
                    return false;
                }
            } catch (Exception ignored) { return false; }

            if (channel.equals("minecraft:brand") || channel.equals("MC|Brand")) {
                if (brandCheckEnabled) {
                    String brand = readString(p.data.copy());
                    if (brand.length() > maxBrandLength) {
                        flag("Oversized Client Brand", packetName);
                        return false;
                    }
                    if (blockInvalidBrandChars && !brand.matches("[a-zA-Z0-9_ .-]+")) {
                        flag("Invalid Brand Characters", packetName);
                        return false;
                    }
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
        } catch (Exception e) { return ""; } finally { buf.release(); }
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