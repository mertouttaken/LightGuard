package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.List;
import java.util.Locale;

/**
 * Guards command tab-completion.
 *
 * <p>The previous version rejected any buffer containing {@code @ = [ ] { }}, which meant
 * tab-completing {@code /minecraft:give} or any selector argument dropped the packet and recorded a
 * violation — twelve of those inside four seconds and ViolationRadar kicked the player. Those are
 * ordinary command syntax, so screening is now limited to expression-evaluator crashers, injection
 * payloads, a length ceiling and a rate limit, plus whatever the operator lists in config.
 */
public class TabCheck extends Check {

    private static final int MAX_REQUESTS_PER_SECOND = 50;

    /** Plugin commands that evaluate their argument as an expression; the classic tab crashers. */
    private static final String[] EXPRESSION_COMMANDS = {"//calc", "//eval", "//solve", "//math"};

    private final int maxLength;
    private final List<String> blockedSubstrings;

    public TabCheck(LightGuard plugin) {
        super(plugin);
        this.maxLength = plugin.getConfig().getInt("checks.tab.max-length", 256);
        this.blockedSubstrings = plugin.getConfig().getStringList("checks.tab.block-contains")
                .stream().map(v -> v.toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundCommandSuggestionPacket)) return true;

        try {
            if (player.incrementAndGetTabCount(System.currentTimeMillis()) > MAX_REQUESTS_PER_SECOND) {
                return false;
            }

            String buffer = ((ServerboundCommandSuggestionPacket) packet).getCommand();
            if (buffer == null || buffer.isEmpty()) return true;

            if (buffer.length() > maxLength) return false;

            String lower = buffer.toLowerCase(Locale.ROOT);

            for (String prefix : EXPRESSION_COMMANDS) {
                if (lower.startsWith(prefix)) return false;
            }

            if (lower.contains("${jndi:") || lower.contains("${ldap:")
                    || lower.contains("${rmi:") || lower.contains("${dns:")) {
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
}
