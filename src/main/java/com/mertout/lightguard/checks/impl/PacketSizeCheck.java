package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.*;

import java.lang.reflect.Field;

public class PacketSizeCheck extends Check {

    public PacketSizeCheck(PlayerData data) {
        super(data, "PacketSize");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.packet-size.enabled")) return true;

        int maxString = plugin.getConfig().getInt("checks.packet-size.max-string-length", 10000);
        int maxBuffer = plugin.getConfig().getInt("checks.packet-size.max-buffer-size", 16384);

        String packetName = packet.getClass().getSimpleName();

        // 1. Chat Packet Size
        if (packet instanceof PacketPlayInChat) {
            String msg = getStringField(packet, "a");
            if (msg != null && msg.length() > maxString) {
                flag("Oversized Chat Packet", packetName);
                return false;
            }
        }

        // 2. Custom Payload Size (Birleştirilmiş & Fixlenmiş)
        if (packet instanceof PacketPlayInCustomPayload) {
            PacketPlayInCustomPayload payload = (PacketPlayInCustomPayload) packet;

            // Boyut kontrolü
            if (payload.data != null && payload.data.readableBytes() > maxBuffer) {
                // WDL gibi modlara istisna tanınabilir, configden bakılabilir
                // Şimdilik genel koruma:
                flag("Oversized Payload (" + payload.data.readableBytes() + " bytes)", packetName);
                return false;
            }
        }

        // 3. BEdit (Book Edit) Size
        if (packet instanceof PacketPlayInBEdit) {
            ItemStack item = getItemField(packet, "a"); // 1.16.5'te genelde 'a' itemdir
            // Eğer item null değilse ve NBT string boyutu limiti aşıyorsa
            if (item != null && item.hasTag()) {
                if (item.getTag().toString().length() > maxBuffer) {
                    flag("Oversized Book Packet", packetName);
                    return false;
                }
            }
        }

        // 4. Tab Complete Size
        if (packet instanceof PacketPlayInTabComplete) {
            // 1.16.5'te 'a' TransactionID(int), 'b' String(text) olabilir.
            // Önce 'b' (yaygın olan) sonra 'a' denenir.
            String text = getStringField(packet, "b");
            if (text == null) text = getStringField(packet, "a");

            if (text != null && text.length() > 256) {
                flag("Oversized Tab Complete (" + text.length() + ")", packetName);
                return false;
            }
        }

        return true;
    }

    private String getStringField(Object obj, String name) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object value = f.get(obj);
            if (value instanceof String) {
                return (String) value;
            }
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