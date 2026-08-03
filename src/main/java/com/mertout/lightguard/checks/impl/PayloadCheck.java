package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import com.mertout.lightguard.netty.nbt.NBTStreamingScanner;
import net.minecraft.server.v1_16_R3.MinecraftKey;
import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import io.netty.buffer.ByteBuf;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.regex.Pattern;

public class PayloadCheck extends Check {

    private static final VarHandle TAG_FIELD;
    private static final VarHandle DATA_FIELD;

    static {
        VarHandle tag = null;
        VarHandle data = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInCustomPayload.class, MethodHandles.lookup());
            for (Field field : PacketPlayInCustomPayload.class.getDeclaredFields()) {
                if (field.getType() == MinecraftKey.class) {
                    field.setAccessible(true);
                    tag = lookup.unreflectVarHandle(field);
                } else if (ByteBuf.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    data = lookup.unreflectVarHandle(field);
                }
            }
        } catch (Exception ignored) {}
        TAG_FIELD = tag;
        DATA_FIELD = data;
    }

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
        if (!(packet instanceof PacketPlayInCustomPayload)) return true;

        try {
            if (TAG_FIELD == null || DATA_FIELD == null) return true;

            Object tagObj = TAG_FIELD.get(packet);
            String channel = tagObj != null ? tagObj.toString() : "";

            if (!isValidChannel(channel)) {
                return false;
            }

            if (channel.equalsIgnoreCase("BungeeCord") || channel.equalsIgnoreCase("bungeecord:main")) {
                return false;
            }

            for (String blocked : blockedChannelsArray) {
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

            ByteBuf data = (ByteBuf) DATA_FIELD.get(packet);

            if (channel.equalsIgnoreCase("minecraft:register") || channel.equalsIgnoreCase("REGISTER")) {
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

            if (data != null && data.refCnt() > 0 && data.readableBytes() > 0) {
                if (data.readableBytes() > maxPayloadSize) {
                    return false;
                }

                if (!NBTStreamingScanner.scan(data)) {
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

        if (channel.equals("REGISTER") || channel.equals("UNREGISTER")) return true;
        if (channel.startsWith("MC|") || channel.startsWith("FML|")) return true;

        return NAMESPACE_PATTERN.matcher(channel).matches();
    }
}