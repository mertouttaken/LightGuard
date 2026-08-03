package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInTabComplete;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class TabCheck extends Check {

    private static final VarHandle BUFFER_FIELD;

    static {
        VarHandle buffer = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInTabComplete.class, MethodHandles.lookup());
            for (Field field : PacketPlayInTabComplete.class.getDeclaredFields()) {
                if (field.getType() == String.class) {
                    field.setAccessible(true);
                    buffer = lookup.unreflectVarHandle(field);
                    break;
                }
            }
        } catch (Exception ignored) {}
        BUFFER_FIELD = buffer;
    }

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
        if (!(packet instanceof PacketPlayInTabComplete)) return true;

        try {
            if (BUFFER_FIELD == null) return true;

            long now = System.currentTimeMillis();

            if (player.incrementAndGetTabCount(now) > 50) {
                return false;
            }

            String buffer = (String) BUFFER_FIELD.get(packet);
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