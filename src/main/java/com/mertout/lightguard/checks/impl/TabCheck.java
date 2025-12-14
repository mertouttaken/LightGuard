package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInTabComplete;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;

public class TabCheck extends Check {

    private static final VarHandle BUFFER_FIELD;
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInTabComplete.class, MethodHandles.lookup());
            BUFFER_FIELD = lookup.findVarHandle(PacketPlayInTabComplete.class, "b", String.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

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
            String buffer = (String) BUFFER_FIELD.get(packet);
            if (buffer == null) return true;

            if (buffer.length() > maxLength) {
                flag("Oversized Tab", "PacketPlayInTabComplete");
                return false;
            }
            for (String s : blockedChars) {
                if (buffer.contains(s)) {
                    flag("Illegal Character", "PacketPlayInTabComplete");
                    return false;
                }
            }
            String lowerBuffer = buffer.toLowerCase();
            for (String s : blockedSubstrings) {
                if (lowerBuffer.contains(s)) {
                    flag("Malicious Syntax", "PacketPlayInTabComplete");
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
}