package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInChat;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

public class ChatSecurityCheck extends Check {

    // 1. ZALGO: Üst üste binen glitch karakterler (Görsel Spam)
    private static final Pattern ZALGO_PATTERN = Pattern.compile("[\\u0300-\\u036F\\u0483-\\u0489\\u1DC0-\\u1DFF\\u20D0-\\u20FF\\uFE20-\\uFE2F]{3,}");

    // 2. ASCII CONTROL: Sistem çökerten karakterler (Null, Bell, Escape vb.)
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

    // 3. BLOCKED RANGES (Senin Eklediğin): Görünmez ve Yön Değiştirici Karakterler
    private static final int[][] BLOCKED_RANGES = {
            {0x202A, 0x202E}, // Directional formatting (Yazıyı tersten yazdırma - Admin taklidi)
            {0x200B, 0x200D}, // Zero-width characters (Görünmez boşluk - "K.ü.f.ü.r" filtresini delmek için)
            {0xFFF0, 0xFFFF}, // Specials (Gereksiz Unicode sonu karakterleri)
            {0xFEFF, 0xFEFF}  // Byte order mark (Görünmez başlangıç işareti)
    };

    private static Field chatMessageField;

    static {
        try {
            chatMessageField = PacketPlayInChat.class.getDeclaredField("a");
            chatMessageField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ChatSecurityCheck(PlayerData data) {
        super(data, "ChatSecurity");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.chat-unicode.enabled")) return true;

        if (packet instanceof PacketPlayInChat) {
            try {
                String message = (String) chatMessageField.get(packet);
                if (message == null || message.isEmpty()) return true;

                // --- 1. ASCII Kontrol Karakterleri ---
                if (CONTROL_CHARS.matcher(message).find()) {
                    flag("Illegal Control Characters", "Chat");
                    return false;
                }

                // --- 2. Zalgo (Glitch Text) ---
                if (plugin.getConfig().getBoolean("checks.chat-unicode.block-zalgo", true)) {
                    if (ZALGO_PATTERN.matcher(message).find()) {
                        flag("Zalgo/Glitch Text Detected", "Chat");
                        return false;
                    }
                }

                // --- 3. Unicode Range Kontrolü (Senin Kodun) ---
                if (containsBlockedUnicode(message)) {
                    flag("Illegal Unicode Character", "Chat");
                    return false;
                }

                // --- 4. Uzunluk Kontrolü ---
                if (message.length() > 256) {
                    flag("Message too long", "Chat");
                    return false;
                }

            } catch (Exception e) {
                // Reflection hatası
            }
        }
        return true;
    }

    // Mesajın içinde yasaklı aralıktan karakter var mı?
    private boolean containsBlockedUnicode(String message) {
        int len = message.length();
        for (int i = 0; i < len; i++) {
            char c = message.charAt(i);

            // Senin belirlediğin aralıkları kontrol et
            for (int[] range : BLOCKED_RANGES) {
                if (c >= range[0] && c <= range[1]) {
                    return true; // Yasaklı karakter bulundu
                }
            }
        }
        return false;
    }
}