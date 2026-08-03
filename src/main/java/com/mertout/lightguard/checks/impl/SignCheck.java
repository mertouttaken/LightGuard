package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.PacketPlayInUpdateSign;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.regex.Pattern;

public class SignCheck extends Check {

    private static final VarHandle LINES_FIELD;
    private static final VarHandle POS_FIELD;

    static {
        VarHandle pos = null;
        VarHandle lines = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInUpdateSign.class, MethodHandles.lookup());
            for (Field field : PacketPlayInUpdateSign.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == BlockPosition.class && pos == null) {
                    pos = lookup.unreflectVarHandle(field);
                } else if (field.getType() == String[].class && lines == null) {
                    lines = lookup.unreflectVarHandle(field);
                }
            }
        } catch (Exception ignored) {}
        POS_FIELD = pos;
        LINES_FIELD = lines;
    }

    private static final Pattern JSON_EXPLOIT_PATTERN = Pattern.compile("[\"'](text|score|selector|extra|translate|clickEvent|hoverEvent|run_command|nbt)[\"']\\s*:");

    private final int maxLineLength;
    private final boolean blockJson;

    public SignCheck(LightGuard plugin) {
        super(plugin);
        this.maxLineLength = plugin.getConfig().getInt("checks.sign.max-line-length", 45);
        this.blockJson = plugin.getConfig().getBoolean("checks.sign.block-json-syntax");
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInUpdateSign)) return true;

        try {
            if (POS_FIELD == null || LINES_FIELD == null) return true;

            BlockPosition pos = (BlockPosition) POS_FIELD.get(packet);
            if (pos == null) return true;

            double pX = player.getLastX();
            double pY = player.getLastY();
            double pZ = player.getLastZ();

            if (pX != -1 && !player.isInTeleportGrace()) {
                double distX = pos.getX() - pX;
                double distY = pos.getY() - pY;
                double distZ = pos.getZ() - pZ;
                double distanceSq = (distX * distX) + (distY * distY) + (distZ * distZ);

                double maxDist = player.isBedrock() ? 225.0 : 100.0;

                if (distanceSq > maxDist) {
                    return false;
                }
            }

            String worldName = player.getCurrentWorldName();

            if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(worldName, pos.getX() >> 4, pos.getZ() >> 4)) {
                return false;
            }

            String[] lines = (String[]) LINES_FIELD.get(packet);
            if (lines == null || lines.length != 4) return true;

            for (String line : lines) {
                if (line == null || line.isEmpty()) continue;

                if (line.length() > maxLineLength) {
                    return false;
                }

                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c < 32 && c != 10 && c != 13) {
                        return false;
                    }
                }

                if (blockJson && JSON_EXPLOIT_PATTERN.matcher(line).find()) {
                    return false;
                }
            }

        } catch (Throwable t) {
            return false;
        }
        return true;
    }
}