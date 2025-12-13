package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInUpdateSign;
import java.lang.reflect.Field;
import java.util.regex.Pattern;

public class SignCheck extends Check {

    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\x00-\\x1F]");
    private static Field linesField;

    static {
        try {
            for (Field f : PacketPlayInUpdateSign.class.getDeclaredFields()) {
                if (f.getType() == String[].class) {
                    f.setAccessible(true);
                    linesField = f;
                    break;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Cache
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
            String[] lines = getLines((PacketPlayInUpdateSign) packet);
            if (lines == null) return true;

            for (String line : lines) {
                if (line == null) continue;

                if (line.length() > maxLineLength) {
                    flag("Oversized Sign Line (" + line.length() + ")", "PacketPlayInUpdateSign");
                    return false;
                }
                if (ILLEGAL_CHARS.matcher(line).find()) {
                    flag("Illegal Characters in Sign", "PacketPlayInUpdateSign");
                    return false;
                }
                if (blockJson) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("{") || line.contains("\"text\"")) {
                        if (line.contains("clickEvent") || line.contains("run_command") || line.contains("score")) {
                            flag("Malicious JSON Sign (Exploit)", "PacketPlayInUpdateSign");
                            return false;
                        }
                        flag("Sign JSON Injection", "PacketPlayInUpdateSign");
                        return false;
                    }
                }
                if (countChars(line, '§') > 5) {
                    flag("Sign Color Spam", "PacketPlayInUpdateSign");
                    return false;
                }
            }
        }
        return true;
    }

    private String[] getLines(PacketPlayInUpdateSign packet) {
        if (linesField == null) return null;
        try { return (String[]) linesField.get(packet); } catch (Exception e) { return null; }
    }

    private int countChars(String str, char c) {
        int count = 0;
        for(int i=0; i < str.length(); i++) { if(str.charAt(i) == c) count++; }
        return count;
    }
}