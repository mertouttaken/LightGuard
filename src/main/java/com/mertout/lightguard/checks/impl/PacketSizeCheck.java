package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.List;

public class PacketSizeCheck extends Check {

    private static VarHandle CHAT_MSG;
    private static VarHandle TAB_TEXT;
    private static VarHandle PAYLOAD_DATA;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            CHAT_MSG = MethodHandles.privateLookupIn(PacketPlayInChat.class, lookup)
                    .findVarHandle(PacketPlayInChat.class, "a", String.class);

            TAB_TEXT = MethodHandles.privateLookupIn(PacketPlayInTabComplete.class, lookup)
                    .findVarHandle(PacketPlayInTabComplete.class, "b", String.class);

            String payloadFieldName = findFieldByType(PacketPlayInCustomPayload.class, PacketDataSerializer.class, "data", "b");
            if (payloadFieldName != null) {
                PAYLOAD_DATA = MethodHandles.privateLookupIn(PacketPlayInCustomPayload.class, lookup)
                        .findVarHandle(PacketPlayInCustomPayload.class, payloadFieldName, PacketDataSerializer.class);
            }

        } catch (Exception e) {
            System.err.println("Reflection failed in PacketSizeCheck: " + e.getMessage());
        }
    }

    private final List<String> excludedPackets;
    private final int maxStringLength;
    private final int globalLimit;

    public PacketSizeCheck(LightGuard plugin) {
        super(plugin);
        this.excludedPackets = plugin.getConfig().getStringList("checks.packet-size.excluded-packets");
        this.maxStringLength = plugin.getConfig().getInt("checks.packet-size.max-string-length", 256);
        this.globalLimit = plugin.getConfig().getInt("checks.packet-size.per-packet-limit", 32000);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        String packetName = packet.getClass().getSimpleName();
        if (excludedPackets.contains(packetName)) return true;

        try {
            if (packet instanceof PacketPlayInChat && CHAT_MSG != null) {
                String msg = (String) CHAT_MSG.get(packet);
                if (msg != null && msg.length() > maxStringLength) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Chat Message");
                    return false;
                }
            }

            if (packet instanceof PacketPlayInTabComplete && TAB_TEXT != null) {
                String text = (String) TAB_TEXT.get(packet);
                if (text != null && text.length() > 256) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Tab Complete Request");
                    return false;
                }
            }

            if (packet instanceof PacketPlayInCustomPayload && PAYLOAD_DATA != null) {
                PacketDataSerializer data = (PacketDataSerializer) PAYLOAD_DATA.get(packet);
                if (data != null && data.readableBytes() > globalLimit) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Custom Payload");
                    return false;
                }
            }

            if (packet instanceof PacketPlayInUpdateSign) {
                PacketPlayInUpdateSign signPacket = (PacketPlayInUpdateSign) packet;
                String[] lines = signPacket.c();
                int totalChars = 0;
                for (String line : lines) {
                    if (line != null) totalChars += line.length();
                }
                if (totalChars > 1000) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Sign Data");
                    return false;
                }
            }

        } catch (Exception ignored) {}
        return true;
    }

    private static String findFieldByType(Class<?> clazz, Class<?> type, String... preferredNames) {
        for (String name : preferredNames) {
            try {
                Field f = clazz.getDeclaredField(name);
                if (type.isAssignableFrom(f.getType())) return name;
            } catch (NoSuchFieldException ignored) {}
        }
        for (Field f : clazz.getDeclaredFields()) {
            if (type.isAssignableFrom(f.getType())) return f.getName();
        }
        return null;
    }
}