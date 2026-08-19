package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.regex.Pattern;

public class PayloadCheck extends Check {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_\\-]+:[a-z0-9_\\-.]+");

    private final String[] blockedChannelsArray;
    private final int maxPayloadSize;

    public PayloadCheck(LightGuard plugin) {
        super(plugin);
        this.blockedChannelsArray = plugin.getConfig().getStringList("checks.payload.blocked-channels").toArray(new String[0]);
        this.maxPayloadSize = plugin.getConfig().getInt("checks.payload.max-size", 2048);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundCustomPayloadPacket)) return true;

        try {
            ServerboundCustomPayloadPacket payloadPacket = (ServerboundCustomPayloadPacket) packet;
            CustomPacketPayload payload = payloadPacket.payload();
            ResourceLocation id = payload.type().id();
            String channel = id.toString();

            if (!isValidChannel(channel)) {
                return false;
            }

            if (channel.equalsIgnoreCase("bungeecord") || channel.equalsIgnoreCase("bungeecord:main")) {
                return false;
            }

            for (String blocked : blockedChannelsArray) {
                if (channel.equalsIgnoreCase(blocked)) {
                    return false;
                }
            }

            if (channel.equalsIgnoreCase("minecraft:bedit") || channel.equalsIgnoreCase("minecraft:bsign")) {
                if (!player.isHoldingBook()) {
                    return false;
                }
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }

    private boolean isValidChannel(String channel) {
        if (channel == null || channel.isEmpty() || channel.length() > 128) return false;

        for (int i = 0; i < channel.length(); i++) {
            if (Character.isISOControl(channel.charAt(i))) return false;
        }

        if (channel.equals("register") || channel.equals("unregister")) return true;
        if (channel.startsWith("mc|") || channel.startsWith("fml|")) return true;

        return NAMESPACE_PATTERN.matcher(channel).matches();
    }
}