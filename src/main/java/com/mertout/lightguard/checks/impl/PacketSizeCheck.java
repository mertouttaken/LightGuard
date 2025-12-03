package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import io.netty.buffer.ByteBuf;
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

        // 1. Chat Packet Size
        if (packet instanceof PacketPlayInChat) {
            String msg = getStringField(packet, "a");
            if (msg != null && msg.length() > maxString) {
                flag("Oversized Chat Packet");
                return false;
            }
        }

        // 2. Custom Payload Size
        if (packet instanceof PacketPlayInCustomPayload) {
            PacketPlayInCustomPayload payload = (PacketPlayInCustomPayload) packet;
            if (payload.data != null && payload.data.readableBytes() > maxBuffer) {
                flag("Oversized Payload Packet");
                return false;
            }
        }

        // 3. BEdit (Book Edit) Size
        if (packet instanceof PacketPlayInBEdit) {
            // Kitap içeriği çok büyükse
            ItemStack item = getItemField(packet, "a");
            if (item != null && item.hasTag() && item.getTag().toString().length() > maxBuffer) {
                flag("Oversized Book Packet");
                return false;
            }
        }
        if (packet instanceof PacketPlayInTabComplete) {
            String text = getStringField(packet, "a");
            // Tab tamamlama için 256 karakterden fazlası gereksizdir
            if (text != null && text.length() > 256) {
                flag("Oversized Tab Complete (" + text.length() + ")");
                return false;
            }
        }

        // ➤ FIX: Payload ve Diğerleri için Genel Buffer Check
        // Eğer paket CustomPayload ise ayrıca kanal ismine bak (Test 3 - Minecraft:Bedit)
        if (packet instanceof PacketPlayInCustomPayload) {
            PacketPlayInCustomPayload p = (PacketPlayInCustomPayload) packet;
            String channel = p.tag.toString();
            // Config'deki yasaklı kanalları burada da kontrol et (veya PayloadCheck'te)
            // Ama en önemlisi boyut kontrolü:
            if (p.data.readableBytes() > maxBuffer) {
                flag("Oversized Payload");
                return false;
            }
        }

        return true;
    }

    private String getStringField(Object obj, String name) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (String) f.get(obj);
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