package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInChat;
import java.lang.reflect.Field;
import java.util.regex.Pattern;

public class ChatSecurityCheck extends Check {

    private static final Pattern ZALGO_PATTERN = Pattern.compile("[\\u0300-\\u036F\\u0483-\\u0489\\u1DC0-\\u1DFF\\u20D0-\\u20FF\\uFE20-\\uFE2F]{3,}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");
    private static final int[][] BLOCKED_RANGES = {
            {0x202A, 0x202E}, {0x200B, 0x200D}, {0xFFF0, 0xFFFF}, {0xFEFF, 0xFEFF}
    };
    private static Field chatMessageField;

    static {
        try {
            chatMessageField = PacketPlayInChat.class.getDeclaredField("a");
            chatMessageField.setAccessible(true);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private final boolean blockZalgo;

    public ChatSecurityCheck(PlayerData data) {
        super(data, "ChatSecurity", "chat-unicode");
        this.blockZalgo = plugin.getConfig().getBoolean("checks.chat-unicode.block-zalgo", true);
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInChat) {
            try {
                String message = (String) chatMessageField.get(packet);
                if (message == null || message.isEmpty()) return true;

                if (CONTROL_CHARS.matcher(message).find()) {
                    flag("Illegal Control Characters", "PacketPlayInChat");
                    return false;
                }

                if (blockZalgo) {
                    if (ZALGO_PATTERN.matcher(message).find()) {
                        flag("Zalgo/Glitch Text Detected", "PacketPlayInChat");
                        return false;
                    }
                }

                if (containsBlockedUnicode(message)) {
                    flag("Illegal Unicode Character", "PacketPlayInChat");
                    return false;
                }

                if (message.length() > 256) {
                    flag("Message too long", "PacketPlayInChat");
                    return false;
                }

            } catch (Exception e) {}
        }
        return true;
    }

    private boolean containsBlockedUnicode(String message) {
        int len = message.length();
        for (int i = 0; i < len; i++) {
            char c = message.charAt(i);
            for (int[] range : BLOCKED_RANGES) {
                if (c >= range[0] && c <= range[1]) return true;
            }
        }
        return false;
    }
}