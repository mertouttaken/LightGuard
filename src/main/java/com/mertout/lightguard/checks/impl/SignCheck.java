package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.PacketPlayInUpdateSign;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.regex.Pattern;

public class SignCheck extends Check {

    private static final VarHandle LINES_FIELD;
    private static final VarHandle POS_FIELD;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInUpdateSign.class, MethodHandles.lookup());
            POS_FIELD = lookup.findVarHandle(PacketPlayInUpdateSign.class, "a", BlockPosition.class);
            LINES_FIELD = lookup.findVarHandle(PacketPlayInUpdateSign.class, "b", String[].class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\x00-\\x1F]");
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
            BlockPosition pos = (BlockPosition) POS_FIELD.get(packet);
            if (pos == null) return true;

            double pX = player.getLastX();
            double pY = player.getLastY();
            double pZ = player.getLastZ();

            if (pX != -1) {
                double distX = pos.getX() - pX;
                double distY = pos.getY() - pY;
                double distZ = pos.getZ() - pZ;
                double distanceSq = (distX * distX) + (distY * distY) + (distZ * distZ);

                double maxDist = player.isBedrock() ? 225.0 : 100.0;

                if (distanceSq > maxDist) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Sign Edit too far");
                    return false;
                }
            }

            String worldName = player.getCurrentWorldName();

            if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(worldName, pos.getX() >> 4, pos.getZ() >> 4)) {
                return false;
            }

            String[] lines = (String[]) LINES_FIELD.get(packet);
            if (lines == null) return true;

            for (String line : lines) {
                if (line == null) continue;

                if (line.length() > maxLineLength) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Sign Line");
                    return false;
                }

                if (ILLEGAL_CHARS.matcher(line).find()) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Illegal Chars in Sign");
                    return false;
                }

                if (blockJson && JSON_EXPLOIT_PATTERN.matcher(line).find()) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Dangerous JSON in Sign");
                    return false;
                }
            }

        } catch (Exception e) {
            return false;
        }
        return true;
    }
}