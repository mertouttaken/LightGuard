package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInUpdateSign;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

public class SignCheck extends Check {

    // ➤ YENİ: Illegal Karakterler (Client Crash Fix)
    // 0x00-0x1F arası kontrol karakterlerini engeller.
    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\x00-\\x1F]");

    // ➤ OPTİMİZASYON: Reflection Field Cache
    // Her pakette tekrar tekrar field aramak yerine bir kez bulup saklıyoruz.
    private static Field linesField;

    static {
        try {
            // 1.16.5'te 'String[]' tipindeki field'ı bul ve kaydet.
            for (Field f : PacketPlayInUpdateSign.class.getDeclaredFields()) {
                if (f.getType() == String[].class) {
                    f.setAccessible(true);
                    linesField = f;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public SignCheck(PlayerData data) {
        super(data, "Sign");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.sign.enabled")) return true;

        if (packet instanceof PacketPlayInUpdateSign) {
            PacketPlayInUpdateSign sign = (PacketPlayInUpdateSign) packet;
            String packetName = "PacketPlayInUpdateSign";

            // Optimize edilmiş getter kullanıyoruz
            String[] lines = getLines(sign);

            if (lines == null) return true; // Okuyamazsak geç (Safe fail)

            for (String line : lines) {
                if (line == null) continue;

                // ➤ 1. Uzunluk Kontrolü (Senin Kodun)
                if (line.length() > plugin.getConfig().getInt("checks.sign.max-line-length", 45)) {
                    flag("Oversized Sign Line (" + line.length() + ")", packetName);
                    return false;
                }

                // ➤ 2. Illegal Karakter Kontrolü (YENİ - Client Crash Fix)
                if (ILLEGAL_CHARS.matcher(line).find()) {
                    flag("Illegal Characters in Sign", packetName);
                    return false;
                }

                // ➤ 3. JSON Injection Kontrolü (Geliştirilmiş)
                if (plugin.getConfig().getBoolean("checks.sign.block-json-syntax")) {
                    String trimmed = line.trim();

                    // Temel JSON tespiti
                    if (trimmed.startsWith("{") || line.contains("\"text\"")) {

                        // Ekstra Güvenlik: Scoreboard veya Komut exploiti var mı?
                        if (line.contains("clickEvent") || line.contains("run_command") || line.contains("score")) {
                            flag("Malicious JSON Sign (Exploit)", packetName);
                            return false;
                        }

                        flag("Sign JSON Injection", packetName);
                        return false;
                    }
                }

                // ➤ 4. Renk Kodu Spamı (Senin Kodun)
                // §k§k§k spamı FPS düşürebilir.
                if (countChars(line, '§') > 5) {
                    flag("Sign Color Spam", packetName);
                    return false;
                }
            }
        }
        return true;
    }

    // ➤ OPTİMİZASYON: Hızlı Getter
    // Artık döngüye girmiyor, direkt cache'den çekiyor.
    private String[] getLines(PacketPlayInUpdateSign packet) {
        if (linesField == null) return null;
        try {
            return (String[]) linesField.get(packet);
        } catch (Exception e) {
            return null;
        }
    }

    private int countChars(String str, char c) {
        int count = 0;
        for(int i=0; i < str.length(); i++) { if(str.charAt(i) == c) count++; }
        return count;
    }
}