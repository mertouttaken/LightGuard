package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import com.mertout.lightguard.netty.nbt.NBTStreamingScanner;
import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.regex.Pattern;

public class PayloadCheck extends Check {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_\\-]+:[a-z0-9_\\-.]+");

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
        String channel = p.tag != null ? p.tag.toString() : "";

        if (!isValidChannel(channel)) {
            return false;
        }

        if (channel.equalsIgnoreCase("BungeeCord") || channel.equalsIgnoreCase("bungeecord:main")) {
            return false;
        }

        for (String blocked : blockedChannels) {
            if (channel.equalsIgnoreCase(blocked)) {
                return false;
            }
        }

        if (channel.equalsIgnoreCase("MC|BEdit") || channel.equalsIgnoreCase("minecraft:bedit") ||
                channel.equalsIgnoreCase("MC|BSign") || channel.equalsIgnoreCase("minecraft:bsign")) {

            if (!player.isHoldingBook()) {
                return false;
            }
        }

        if (channel.equalsIgnoreCase("minecraft:register") || channel.equalsIgnoreCase("REGISTER")) {
            ByteBuf data = p.data;
            if (data != null && data.refCnt() > 0) {
                int readableBytes = data.readableBytes();
                int channelCount = 0;

                for (int i = 0; i < readableBytes; i++) {
                    if (data.getByte(data.readerIndex() + i) == 0) channelCount++;
                }
                if (readableBytes > 0) channelCount++;

                if (channelCount > 20) {
                    return false;
                }

                if (player.incrementAndGetChannelRegisterCount(channelCount, System.currentTimeMillis()) > 100) {
                    return false;
                }
            }
        }

        try {
            if (p.data != null && p.data.refCnt() > 0 && p.data.readableBytes() > 0) {

                if (p.data.readableBytes() > maxPayloadSize) {
                    return false;
                }

                if (!NBTStreamingScanner.scan(p.data)) {
                    return false;
                }
            }
        } catch (Exception ignored) {
            return false;
        }

        return true;
    }

    private boolean isValidChannel(String channel) {
        if (channel == null || channel.isEmpty() || channel.length() > 128) return false;

        for (int i = 0; i < channel.length(); i++) {
            if (Character.isISOControl(channel.charAt(i))) return false;
        }

        if (channel.equals("REGISTER") || channel.equals("UNREGISTER")) return true;
        if (channel.startsWith("MC|") || channel.startsWith("FML|")) return true;

        return NAMESPACE_PATTERN.matcher(channel).matches();
    }
}