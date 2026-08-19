package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundChatPacket;

public class ChatSecurityCheck extends Check {

    private final boolean blockZalgo;

    public ChatSecurityCheck(LightGuard plugin) {
        super(plugin);
        this.blockZalgo = plugin.getConfig().getBoolean("checks.chat-unicode.block-zalgo", true);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundChatPacket)) return true;

        try {
            ServerboundChatPacket chatPacket = (ServerboundChatPacket) packet;
            String message = chatPacket.message();

            if (message == null || message.isEmpty()) return true;

            if (containsControlChars(message)) {
                return false;
            }

            if (containsBlockedUnicode(message)) {
                return false;
            }

            if (blockZalgo && containsZalgo(message)) {
                return false;
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }

    private boolean containsControlChars(String message) {
        int len = message.length();
        for (int i = 0; i < len; i++) {
            char c = message.charAt(i);
            if (c <= 0x08 || c == 0x0B || c == 0x0C || (c >= 0x0E && c <= 0x1F)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsBlockedUnicode(String message) {
        int len = message.length();
        for (int i = 0; i < len; i++) {
            char c = message.charAt(i);
            if ((c >= 0x202A && c <= 0x202E) || (c >= 0x200B && c <= 0x200D) || c >= 0xFFF0) return true;
        }
        return false;
    }

    private boolean containsZalgo(String message) {
        int consecutiveCombiningMarks = 0;
        int len = message.length();

        for (int i = 0; i < len; i++) {
            char c = message.charAt(i);

            if ((c >= '\u0300' && c <= '\u036F') ||
                    (c >= '\u0483' && c <= '\u0489') ||
                    (c >= '\u1DC0' && c <= '\u1DFF') ||
                    (c >= '\u20D0' && c <= '\u20FF') ||
                    (c >= '\uFE20' && c <= '\uFE2F')) {

                consecutiveCombiningMarks++;
                if (consecutiveCombiningMarks >= 3) {
                    return true;
                }
            } else {
                consecutiveCombiningMarks = 0;
            }
        }
        return false;
    }
}