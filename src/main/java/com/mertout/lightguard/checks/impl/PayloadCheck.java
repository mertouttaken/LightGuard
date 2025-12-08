package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PayloadCheck extends Check {

    public PayloadCheck(PlayerData data) {
        super(data, "Payload");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.payload.enabled")) return true;

        if (packet instanceof PacketPlayInCustomPayload) {
            PacketPlayInCustomPayload p = (PacketPlayInCustomPayload) packet;
            String packetName = "PacketPlayInCustomPayload";

            // Kanal Adı (Örn: MC|Brand, WDL|INIT, minecraft:register)
            String channel = p.tag.toString();

            // ➤ 1. Yasaklı Kanallar (Blacklist)
            List<String> blockedChannels = plugin.getConfig().getStringList("checks.payload.blocked-channels");
            for (String blocked : blockedChannels) {
                // "MC|BEdit" veya "minecraft:bedit" gibi
                if (channel.equalsIgnoreCase(blocked) || channel.toLowerCase().contains(blocked.toLowerCase())) {
                    flag("Blocked Channel: " + channel, packetName);
                    return false;
                }
            }

            // ➤ 2. Channel Register Flood (YENİ EKLENEN KRİTİK KISIM)
            // Hileciler binlerce rastgele kanal ismi kaydederek sunucu RAM'ini şişirir.
            if (channel.equals("minecraft:register") || channel.equals("REGISTER")) {
                try {
                    // Veriyi oku (Kopyalamadan stringe çevir, performanslıdır)
                    String channels = p.data.toString(StandardCharsets.UTF_8);

                    // Kanallar "\0" (null byte) ile ayrılır.
                    // Tek seferde 5'ten fazla kanal kaydetmek şüphelidir (Modlar genelde 1-2 tane yollar).
                    if (channels.split("\0").length > 10) {
                        flag("Channel Register Flood", packetName);
                        return false;
                    }
                } catch (Exception e) {
                    return false; // Veri bozuksa engelle
                }
            }

            // ➤ 3. Boyut Kontrolü (Max Size)
            int maxSize = plugin.getConfig().getInt("checks.payload.max-size", 2048);
            if (p.data.readableBytes() > maxSize) {
                // Bazı harita modları (JourneyMap vb.) büyük veri yollayabilir, istisna eklenebilir.
                // Ancak "WDL|INIT" hariç genellikle 2KB üstü gereksizdir.
                if (!channel.equals("WDL|INIT")) {
                    flag("Oversized Payload (" + p.data.readableBytes() + " bytes)", packetName);
                    return false;
                }
            }

            // ➤ 4. Client Brand Protection (Marka Koruması)
            if (channel.equals("minecraft:brand") || channel.equals("MC|Brand")) {
                if (plugin.getConfig().getBoolean("checks.client-brand.enabled")) {
                    String brand = readString(p.data.copy()); // copy() önemli, buffer index bozulmasın

                    // Marka ismi çok uzunsa
                    if (brand.length() > plugin.getConfig().getInt("checks.client-brand.max-length", 20)) {
                        flag("Oversized Client Brand", packetName);
                        return false;
                    }

                    // Geçersiz karakterler
                    if (plugin.getConfig().getBoolean("checks.client-brand.block-invalid-chars")) {
                        if (!brand.matches("[a-zA-Z0-9_ .-]+")) {
                            flag("Invalid Brand Characters", packetName);
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
            if (len > 32767) return ""; // NMS String limiti
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