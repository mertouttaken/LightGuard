package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInTabComplete;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.stream.Collectors;

public class TabCheck extends Check {

    private static final VarHandle BUFFER_FIELD;
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInTabComplete.class, MethodHandles.lookup());
            BUFFER_FIELD = lookup.findVarHandle(PacketPlayInTabComplete.class, "b", String.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private final int maxLength;
    private final List<String> blockedSubstrings;
    private final List<String> blockedChars;

    private static final String[] CRASH_CHARS = {"{", "}", "[", "]", "@", "=", "nbt"};

    public TabCheck(LightGuard plugin) {
        super(plugin);
        this.maxLength = plugin.getConfig().getInt("checks.tab.max-length", 256);

        this.blockedSubstrings = plugin.getConfig().getStringList("checks.tab.block-contains")
                .stream().map(String::toLowerCase).collect(Collectors.toList());

        this.blockedChars = plugin.getConfig().getStringList("checks.tab.block-chars")
                .stream().map(String::toLowerCase).collect(Collectors.toList());
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInTabComplete)) return true;

        long now = System.currentTimeMillis();

        if (player.incrementAndGetTabCount(now) > 50) {
            return false;
        }

        String buffer = (String) BUFFER_FIELD.get(packet);
        if (buffer == null || buffer.isEmpty()) return true;

        if (buffer.length() > maxLength) {
            return false;
        }

        String lowerBuffer = buffer.toLowerCase();

        if (lowerBuffer.startsWith("//calc") || lowerBuffer.startsWith("//eval") ||
                lowerBuffer.startsWith("//solve") || lowerBuffer.startsWith("//math")) {
            return false;
        }

        for (String c : CRASH_CHARS) {
            if (lowerBuffer.contains(c)) {
                return false;
            }
        }

        for (String c : blockedChars) {
            if (lowerBuffer.contains(c)) {
                return false;
            }
        }

        for (String s : blockedSubstrings) {
            if (lowerBuffer.contains(s)) {
                return false;
            }
        }

        return true;
    }
}