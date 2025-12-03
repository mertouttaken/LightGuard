package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

public class PayloadCheck extends Check {

    public PayloadCheck(PlayerData data) {
        super(data, "Payload");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.payload.enabled")) return true;

        if (packet instanceof PacketPlayInCustomPayload) {
            PacketPlayInCustomPayload p = (PacketPlayInCustomPayload) packet;

            // Kanal Adı (Örn: MC|Brand, WDL|INIT)
            String channel = p.tag.toString();

            // 1. Yasaklı Kanallar (Blacklist)
            if (plugin.getConfig().getStringList("checks.payload.blocked-channels").contains(channel)) {
                flag("Blocked Channel: " + channel);
                return false;
            }

            // 2. Boyut Kontrolü (Max Size)
            // Config'den limiti al, yoksa varsayılan 2048
            int maxSize = plugin.getConfig().getInt("checks.payload.max-size", 2048);
            if (p.data.readableBytes() > maxSize) {
                flag("Oversized Payload (" + p.data.readableBytes() + " bytes)");
                return false;
            }

            // 3. Client Brand Protection (Marka Koruması)
            if (channel.equals("minecraft:brand") || channel.equals("MC|Brand")) {
                if (plugin.getConfig().getBoolean("checks.client-brand.enabled")) {
                    // ByteBuf'u kopyalayıp okuyalım (Orijinal veriyi bozmamak için)
                    String brand = readString(p.data.copy());

                    // Marka ismi çok uzunsa
                    if (brand.length() > plugin.getConfig().getInt("checks.client-brand.max-length", 20)) {
                        flag("Oversized Client Brand");
                        return false;
                    }

                    // Geçersiz karakterler
                    if (plugin.getConfig().getBoolean("checks.client-brand.block-invalid-chars")) {
                        if (!brand.matches("[a-zA-Z0-9_ .-]+")) {
                            flag("Invalid Brand Characters");
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    // ByteBuf'tan güvenli string okuma
    private String readString(ByteBuf buf) {
        try {
            int len = readVarInt(buf);
            if (len > 32767) return "";
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        } finally {
            buf.release(); // Memory leak önle
        }
    }

    // VarInt okuma yardımcısı
    private int readVarInt(ByteBuf buf) {
        int numRead = 0;
        int result = 0;
        byte read;
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