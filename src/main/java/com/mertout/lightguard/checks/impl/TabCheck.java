package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.List;
import java.util.Locale;

public class TabCheck extends Check {

    private static final int MAX_REQUESTS_PER_SECOND = 15;
    private static final int MAX_BRACKET_DEPTH = 15;

    private static final String[] EXPRESSION_COMMANDS = {
            "calc", "eval", "solve", "math", "hd readtext", "pex", "execute", "function"
    };

    private final int maxLength;
    private final List<String> blockedSubstrings;

    public TabCheck(LightGuard plugin) {
        super(plugin);
        this.maxLength = plugin.getConfig().getInt("checks.tab.max-length", 256);
        this.blockedSubstrings = plugin.getConfig().getStringList("checks.tab.block-contains")
                .stream()
                .map(v -> v.toLowerCase(Locale.ROOT))
                .filter(v -> v.length() > 4 || v.contains("(") || v.contains("."))
                .toList();
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundCommandSuggestionPacket suggestionPacket)) return true;

        try {
            if (player.incrementAndGetTabCount(System.currentTimeMillis()) > MAX_REQUESTS_PER_SECOND) {
                return false;
            }

            String buffer = suggestionPacket.getCommand();
            if (buffer == null || buffer.isEmpty()) return true;

            if (buffer.length() > maxLength) return false;

            int bracketCount = 0;
            int selectorCount = 0;
            for (int i = 0; i < buffer.length(); i++) {
                char c = buffer.charAt(i);
                if (c < 32) {
                    return false;
                }
                if (c == '[' || c == '{') {
                    if (++bracketCount > MAX_BRACKET_DEPTH) {
                        return false;
                    }
                } else if (c == '@' && i + 1 < buffer.length()) {
                    char next = Character.toLowerCase(buffer.charAt(i + 1));
                    if (next == 'e' || next == 'a' || next == 'p' || next == 'r' || next == 's') {
                        if (++selectorCount > 5) {
                            return false;
                        }
                    }
                }
            }

            String lower = buffer.toLowerCase(Locale.ROOT).trim();

            String normalizedCmd = extractBaseCommand(lower);
            for (String blockedCmd : EXPRESSION_COMMANDS) {
                if (normalizedCmd.equals(blockedCmd) || normalizedCmd.startsWith(blockedCmd + " ")) {
                    return false;
                }
            }

            if (lower.contains("${") || lower.contains("jndi:") || lower.contains("ldap:") || lower.contains("rmi:")) {
                return false;
            }

            for (String blocked : blockedSubstrings) {
                if (lower.contains(blocked)) return false;
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }

    private String extractBaseCommand(String input) {
        int start = 0;
        while (start < input.length() && input.charAt(start) == '/') {
            start++;
        }
        String stripped = input.substring(start);
        int spaceIdx = stripped.indexOf(' ');
        String firstToken = spaceIdx == -1 ? stripped : stripped.substring(0, spaceIdx);
        String rest = spaceIdx == -1 ? "" : stripped.substring(spaceIdx);

        int colonIdx = firstToken.lastIndexOf(':');
        if (colonIdx != -1 && colonIdx + 1 < firstToken.length()) {
            firstToken = firstToken.substring(colonIdx + 1);
            while (firstToken.startsWith("/")) {
                firstToken = firstToken.substring(1);
            }
        }
        return firstToken + rest;
    }
}