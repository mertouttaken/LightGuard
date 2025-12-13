package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.*;

import java.lang.reflect.Field;
import java.util.List;

public class PacketSizeCheck extends Check {

    public PacketSizeCheck(PlayerData data) {
        super(data, "PacketSize");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.packet-size.enabled")) return true;

        String packetName = packet.getClass().getSimpleName();

        // ➤ 1. Hardcoded İstisnalar (Performans İçin)
        // CustomPayload zaten PayloadCheck ile korunuyor.
        if (packet instanceof PacketPlayInCustomPayload) {
            return true;
        }

        // ➤ 2. Config İstisnaları (Esneklik İçin - YENİ)
        // Kullanıcı config'e "PacketPlayInMap" yazarsa onu da atlarız.
        List<String> excluded = plugin.getConfig().getStringList("checks.packet-size.excluded-packets");
        if (excluded.contains(packetName)) {
            return true;
        }

        int maxString = plugin.getConfig().getInt("checks.packet-size.max-string-length", 10000);
        int maxBuffer = plugin.getConfig().getInt("checks.packet-size.max-buffer-size", 16384);

        // ➤ 3. Chat Packet Size
        if (packet instanceof PacketPlayInChat) {
            String msg = getStringField(packet, "a");
            if (msg != null && msg.length() > maxString) {
                flag("Oversized Chat Packet (" + msg.length() + ")", packetName);
                return false;
            }
        }

        // ➤ 4. Tab Complete Size
        if (packet instanceof PacketPlayInTabComplete) {
            String text = getStringField(packet, "b");
            if (text == null) text = getStringField(packet, "a");

            if (text != null && text.length() > 256) {
                flag("Oversized Tab Complete (" + text.length() + ")", packetName);
                return false;
            }
        }

        // ➤ 5. BEdit (Book Edit) Size
        if (packet instanceof PacketPlayInBEdit) {
            ItemStack item = getItemField(packet, "a");
            if (item != null && item.hasTag()) {
                if (item.getTag().toString().length() > maxBuffer) {
                    flag("Oversized Book Packet", packetName);
                    return false;
                }
            }
        }

        // ➤ 6. Genel Boyut Tahmini
        long estimatedSize = estimateSize(packet);
        int globalLimit = plugin.getConfig().getInt("checks.packet-size.per-packet-limit", 32000);

        if (estimatedSize > globalLimit) {
            flag("Oversized Packet (" + estimatedSize + " bytes estimate)", packetName);
            return false;
        }

        return true;
    }

    private long estimateSize(Object packet) {
        if (packet instanceof PacketPlayInSetCreativeSlot) return 1500;
        if (packet instanceof PacketPlayInUpdateSign) return 500;
        if (packet instanceof PacketPlayInJigsawGenerate || packet instanceof PacketPlayInStruct) return 2048;
        return 20;
    }

    private String getStringField(Object obj, String name) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object value = f.get(obj);
            if (value instanceof String) return (String) value;
            return null;
        } catch (Exception e) { return null; }
    }

    private ItemStack getItemField(Object obj, String name) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (ItemStack) f.get(obj);
        } catch (Exception e) { return null; }
    }
}