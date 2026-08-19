package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

public class TabCheck extends Check {

    private final int maxLength;
    private final String[] blockedSubstringsArray;
    private final String[] blockedCharsArray;

    private static final String[] CRASH_CHARS = {"{", "}", "[", "]", "@", "=", "nbt"};

    public TabCheck(LightGuard plugin) {
        super(plugin);
        this.maxLength = plugin.getConfig().getInt("checks.tab.max-length", 256);

        this.blockedSubstringsArray = plugin.getConfig().getStringList("checks.tab.block-contains")
                .stream().map(String::toLowerCase).toArray(String[]::new);

        this.blockedCharsArray = plugin.getConfig().getStringList("checks.tab.block-chars")
                .stream().map(String::toLowerCase).toArray(String[]::new);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundCommandSuggestionPacket)) return true;

        try {
            long now = System.currentTimeMillis();

            if (player.incrementAndGetTabCount(now) > 50) {
                return false;
            }

            ServerboundCommandSuggestionPacket tabPacket = (ServerboundCommandSuggestionPacket) packet;
            String buffer = tabPacket.getCommand();

            if (buffer == null || buffer.isEmpty()) return true;

            if (buffer.length() > maxLength) {
                return false;
            }

            for (String c : CRASH_CHARS) {
                if (buffer.contains(c)) {
                    return false;
                }
            }

            String lowerBuffer = buffer.toLowerCase();

            if (lowerBuffer.startsWith("//calc") || lowerBuffer.startsWith("//eval") ||
                    lowerBuffer.startsWith("//solve") || lowerBuffer.startsWith("//math")) {
                return false;
            }

            for (String c : blockedCharsArray) {
                if (lowerBuffer.contains(c)) {
                    return false;
                }
            }

            for (String s : blockedSubstringsArray) {
                if (lowerBuffer.contains(s)) {
                    return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}