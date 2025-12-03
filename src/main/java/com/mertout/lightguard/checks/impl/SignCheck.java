package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInUpdateSign;

import java.lang.reflect.Field;

public class SignCheck extends Check {

    public SignCheck(PlayerData data) {
        super(data, "Sign");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.sign.enabled")) return true;

        if (packet instanceof PacketPlayInUpdateSign) {
            PacketPlayInUpdateSign sign = (PacketPlayInUpdateSign) packet;

            // 1.16.5'te satırlar 'String[]' olarak 'b' field'ında tutulur (genelde).
            String[] lines = getLines(sign);

            if (lines == null) return true; // Okuyamazsak geçelim (Safe fail)

            for (String line : lines) {
                if (line == null) continue;

                // 1. Uzunluk Kontrolü
                if (line.length() > plugin.getConfig().getInt("checks.sign.max-line-length", 45)) {
                    flag("Oversized Sign Line (" + line.length() + ")");
                    return false;
                }

                // 2. JSON Injection Kontrolü
                // Normal bir oyuncu tabelaya {"text":...} yazamaz.
                if (plugin.getConfig().getBoolean("checks.sign.block-json-syntax")) {
                    if (line.trim().startsWith("{") || line.contains("\"text\"")) {
                        flag("Sign JSON Injection");
                        return false;
                    }
                }

                // 3. Renk Kodu Spamı (§k§k§k...)
                // Bu FPS düşürür (Client Crasher)
                if (countChars(line, '§') > 5) {
                    flag("Sign Color Spam");
                    return false;
                }
            }
        }
        return true;
    }

    // NMS'den satırları okumak için Reflection
    private String[] getLines(PacketPlayInUpdateSign packet) {
        try {
            // Field ismi sürüme göre değişebilir, 1.16.5'te genelde 'b' veya 'lines'
            // Garanti olması için String[] tipindeki ilk field'ı alıyoruz.
            for (Field f : packet.getClass().getDeclaredFields()) {
                if (f.getType() == String[].class) {
                    f.setAccessible(true);
                    return (String[]) f.get(packet);
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private int countChars(String str, char c) {
        int count = 0;
        for(int i=0; i < str.length(); i++) { if(str.charAt(i) == c) count++; }
        return count;
    }
}