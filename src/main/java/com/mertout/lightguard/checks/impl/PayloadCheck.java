package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import io.netty.buffer.ByteBuf;
import java.util.*;

public class PayloadCheck extends Check {

    private static final int MAX_CHANNELS_PER_PLAYER = 124;
    private final List<String> blockedChannels;
    private final int maxPayloadSize;

    public PayloadCheck(PlayerData data) {
        super(data, "Payload", "payload");
        this.blockedChannels = plugin.getConfig().getStringList("checks.payload.blocked-channels");
        this.maxPayloadSize = plugin.getConfig().getInt("checks.payload.max-size", 2048);
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInCustomPayload) {
            PacketPlayInCustomPayload p = (PacketPlayInCustomPayload) packet;
            String packetName = "PacketPlayInCustomPayload";

            String channel = p.tag.toString();

            if (channel.length() > 128) {
                flag("Oversized Channel Name (" + channel.length() + " chars)", packetName);
                return false;
            }

            for (String blocked : blockedChannels) {
                if (channel.equalsIgnoreCase(blocked)) {
                    flag("Blocked Channel: " + channel, packetName);
                    return false;
                }
            }

            if (channel.equalsIgnoreCase("MC|BEdit") || channel.equalsIgnoreCase("minecraft:bedit") ||
                    channel.equalsIgnoreCase("MC|BSign") || channel.equalsIgnoreCase("minecraft:bsign")) {

                ByteBuf dataBuffer = p.data;
                if (dataBuffer.readableBytes() > 0) {
                    ByteBuf copy = null;
                    try {
                        copy = dataBuffer.copy();
                        if (copy.readableBytes() > 0) {
                            byte firstByte = copy.readByte();
                            // NBT kökü mutlaka Compound (0x0A) veya End (0x00) olmalı
                            if (firstByte != 0x0A && firstByte != 0x00) {
                                flag("Malformed NBT Root Tag (" + firstByte + ")", packetName);
                                return false;
                            }
                        }
                    } catch (Exception e) {
                        flag("Malformed Payload Read Error", packetName);
                        return false;
                    } finally {
                        if (copy != null) copy.release();
                    }
                }
            }

            if (channel.equalsIgnoreCase("minecraft:register") || channel.equalsIgnoreCase("REGISTER")) {
                ByteBuf data = p.data;
                int readableBytes = data.readableBytes();
                int channelCount = 0;

                for (int i = 0; i < readableBytes; i++) {
                    if (data.getByte(data.readerIndex() + i) == 0) channelCount++;
                }

                if (readableBytes > 0) channelCount++;

                if (channelCount > 20) {
                    flag("Channel Register Flood", packetName);
                    return false;
                }

                long now = System.currentTimeMillis();
                if (now - this.data.getLastChannelRegister().get() > 60000) {
                    this.data.getRecentChannelRegisters().set(0);
                    this.data.getLastChannelRegister().set(now);
                }
                if (this.data.getRecentChannelRegisters().addAndGet(channelCount) > 100) {
                    flag("Channel Register Flood (Rate Limit)", packetName);
                    return false;
                }

                Set<String> channels = this.data.getRegisteredChannels();
                if (channels.size() + channelCount >= MAX_CHANNELS_PER_PLAYER) {
                    flag("Max Channel Limit Reached", packetName);
                    return false;
                }
            }

            try {
                if (p.data.readableBytes() > maxPayloadSize && !channel.equalsIgnoreCase("WDL|INIT")) {
                    flag("Oversized Payload (" + p.data.readableBytes() + " bytes)", packetName);
                    return false;
                }
            } catch (Exception ignored) { return false; }
        }
        return true;
    }
}