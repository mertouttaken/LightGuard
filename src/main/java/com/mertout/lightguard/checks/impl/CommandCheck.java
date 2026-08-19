package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Screens outgoing commands against a blacklist and for injection payloads.
 *
 * <p>Handles both command packets: unsigned, and the signed variant sent whenever any argument is
 * signable (1.20.5+). Registering only the unsigned form previously let any signed command skip
 * this check entirely. Chat is not inspected here — chat has not carried commands since 1.19.
 */
public class CommandCheck extends Check {

    private static final int MAX_COMMAND_LENGTH = 256;

    private final boolean blockSyntax;
    private final Set<String> blacklist = new HashSet<>();

    public CommandCheck(LightGuard plugin) {
        super(plugin);
        this.blockSyntax = plugin.getConfig().getBoolean("checks.command.block-syntax", true);

        for (String entry : plugin.getConfig().getStringList("checks.command.blacklist")) {
            String normalised = entry.toLowerCase(Locale.ROOT);
            while (normalised.startsWith("/")) {
                normalised = normalised.substring(1);
            }
            if (!normalised.isEmpty()) blacklist.add(normalised);
        }
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        String rawMsg;

        if (packet instanceof ServerboundChatCommandPacket) {
            rawMsg = ((ServerboundChatCommandPacket) packet).command();
        } else if (packet instanceof ServerboundChatCommandSignedPacket) {
            rawMsg = ((ServerboundChatCommandSignedPacket) packet).command();
        } else {
            return true;
        }

        try {
            if (rawMsg == null) return true;
            if (rawMsg.length() > MAX_COMMAND_LENGTH) return false;

            if (containsLog4Shell(rawMsg)) return false;

            // WorldEdit-style crashers chain expressions with "::".
            if (blockSyntax && rawMsg.contains("::")) return false;

            int spaceIndex = rawMsg.indexOf(' ');
            String rawCmd = (spaceIndex == -1 ? rawMsg : rawMsg.substring(0, spaceIndex)).toLowerCase(Locale.ROOT);

            // Homoglyph and full-width variants normalise back to ASCII, so "／ｏｐ" cannot slip past
            // a literal comparison against "op".
            String stripped = stripToAscii(Normalizer.normalize(rawCmd, Normalizer.Form.NFKC));

            return !isBlacklisted(rawCmd) && !isBlacklisted(stripped);

        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isBlacklisted(String command) {
        while (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (blacklist.contains(command)) return true;

        int colon = command.indexOf(':');
        return colon != -1 && blacklist.contains(command.substring(colon + 1));
    }

    private static String stripToAscii(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c <= 127) sb.append(c);
        }
        return sb.toString();
    }

    private boolean containsLog4Shell(String msg) {
        if (msg == null || msg.isEmpty()) return false;
        String lower = msg.toLowerCase(Locale.ROOT);
        return lower.contains("${jndi:") || lower.contains("${ldap:") ||
                lower.contains("${rmi:") || lower.contains("${dns:");
    }
}
