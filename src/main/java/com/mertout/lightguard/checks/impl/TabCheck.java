package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInTabComplete;
import java.util.List;

public class TabCheck extends Check {

    private final int maxLength;
    private final List<String> blockedChars;
    private final List<String> blockedSubstrings;
    private final List<String> blacklistedCmds;

    public TabCheck(PlayerData data) {
        super(data, "Tab", "tab");
        this.maxLength = plugin.getConfig().getInt("checks.tab.max-length", 256);
        this.blockedChars = plugin.getConfig().getStringList("checks.tab.block-chars");
        this.blockedSubstrings = plugin.getConfig().getStringList("checks.tab.block-contains");
        this.blacklistedCmds = plugin.getConfig().getStringList("checks.tab.blacklisted-commands");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInTabComplete) {
            String buffer = getBuffer((PacketPlayInTabComplete) packet);
            String packetName = "PacketPlayInTabComplete";

            if (buffer == null) return true;

            if (buffer.length() > maxLength) {
                flag("Oversized Tab Request (" + buffer.length() + ")", packetName);
                return false;
            }

            for (String s : blockedChars) {
                if (buffer.contains(s)) {
                    flag("Illegal Character in Tab", packetName);
                    return false;
                }
            }

            String lowerBuffer = buffer.toLowerCase();
            for (String s : blockedSubstrings) {
                if (lowerBuffer.contains(s.toLowerCase())) {
                    flag("Malicious Syntax in Tab (" + s + ")", packetName);
                    return false;
                }
            }

            for (String cmd : blacklistedCmds) {
                if (lowerBuffer.startsWith(cmd.toLowerCase())) {
                    return false;
                }
            }
        }
        return true;
    }

    private String getBuffer(PacketPlayInTabComplete p) {
        try {
            java.lang.reflect.Field f = p.getClass().getDeclaredField("a");
            f.setAccessible(true);
            return (String) f.get(p);
        } catch (Exception e) { return ""; }
    }
}