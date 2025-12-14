package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInUpdateSign;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.regex.Pattern;

public class SignCheck extends Check {

    private static final VarHandle LINES_FIELD;
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInUpdateSign.class, MethodHandles.lookup());
            LINES_FIELD = lookup.findVarHandle(PacketPlayInUpdateSign.class, "b", String[].class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\x00-\\x1F]");
    private final int maxLineLength;
    private final boolean blockJson;

    public SignCheck(PlayerData data) {
        super(data, "Sign", "sign");
        this.maxLineLength = plugin.getConfig().getInt("checks.sign.max-line-length", 45);
        this.blockJson = plugin.getConfig().getBoolean("checks.sign.block-json-syntax");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;
        if (packet instanceof PacketPlayInUpdateSign) {
            String[] lines = (String[]) LINES_FIELD.get(packet);
            if (lines == null) return true;

            for (String line : lines) {
                if (line == null) continue;
                if (line.length() > maxLineLength) {
                    flag("Oversized Sign Line", "PacketPlayInUpdateSign");
                    return false;
                }
                if (ILLEGAL_CHARS.matcher(line).find()) {
                    flag("Illegal Chars", "PacketPlayInUpdateSign");
                    return false;
                }
                if (blockJson && (line.trim().startsWith("{") || line.contains("\"text\""))) {
                    flag("JSON Injection", "PacketPlayInUpdateSign");
                    return false;
                }
            }
        }
        return true;
    }
}