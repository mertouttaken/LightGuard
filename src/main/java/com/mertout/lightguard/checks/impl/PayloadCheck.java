package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import io.netty.buffer.ByteBuf;
import java.util.*;

public class PayloadCheck extends Check {

    private static final int MAX_CHANNELS_PER_PLAYER = 124;
    private final List<String> blockedChannels;
    private final int maxPayloadSize;

    public PayloadCheck(LightGuard plugin) {
        super(plugin);
        this.blockedChannels = plugin.getConfig().getStringList("checks.payload.blocked-channels");
        this.maxPayloadSize = plugin.getConfig().getInt("checks.payload.max-size", 2048);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInCustomPayload)) return true;

        PacketPlayInCustomPayload p = (PacketPlayInCustomPayload) packet;
        String channel = p.tag.toString();

        if (channel.length() > 128) {
            plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Channel Name");
            return false;
        }

        for (String blocked : blockedChannels) {
            if (channel.equalsIgnoreCase(blocked)) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Blocked Channel Attempt: " + channel);
                return false;
            }
        }

        if (channel.equalsIgnoreCase("MC|BEdit") || channel.equalsIgnoreCase("minecraft:bedit") ||
                channel.equalsIgnoreCase("MC|BSign") || channel.equalsIgnoreCase("minecraft:bsign")) {

            // 🛡️ KRİTİK DÜZELTME: Bukkit API (inv.getItemInMainHand) Netty Thread'de çökme (Race Condition) yapar!
            // Bunun yerine oyuncunun ProtocolPlayer cache'inden okuyoruz.
            if (!player.isHoldingBook()) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Ghost Book Exploit (No Book in Hand)");
                return false;
            }

            ByteBuf dataBuffer = p.data;
            if (dataBuffer.readableBytes() > 0) {
                ByteBuf copy = null;
                try {
                    copy = dataBuffer.copy();
                    if (copy.readableBytes() > 0) {
                        byte firstByte = copy.readByte();
                        if (firstByte != 0x0A && firstByte != 0x00) {
                            plugin.getActionQueue().pushKick(player.getPlayer(), "Malformed NBT Root Tag");
                            return false;
                        }
                    }
                } catch (Exception e) {
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
                plugin.getActionQueue().pushKick(player.getPlayer(), "Channel Register Flood");
                return false;
            }

            if (player.incrementAndGetChannelRegisterCount(channelCount, System.currentTimeMillis()) > 100) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Channel Register Flood (Rate Limit)");
                return false;
            }
        }

        try {
            if (p.data.readableBytes() > maxPayloadSize && !channel.equalsIgnoreCase("WDL|INIT")) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Payload (" + p.data.readableBytes() + " bytes)");
                return false;
            }
        } catch (Exception ignored) { return false; }

        return true;
    }
}