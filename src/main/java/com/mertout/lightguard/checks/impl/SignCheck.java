package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import com.mertout.lightguard.utils.GeyserUtil;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.PacketPlayInUpdateSign;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;

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

    private static final Pattern JSON_EXPLIOT_PATTERN = Pattern.compile("[\"'](text|score|selector|extra|translate|clickEvent|hoverEvent|run_command|nbt)[\"']\\s*:");

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
            try {
                BlockPosition pos = (BlockPosition) POS_FIELD.get(packet);
                Location blockLoc = new Location(data.getPlayer().getWorld(), pos.getX(), pos.getY(), pos.getZ());

                boolean isBedrock = GeyserUtil.isBedrockPlayer(data.getPlayer());
                double maxDist = isBedrock ? 225.0 : 100.0;

                if (data.getPlayer().getLocation().distanceSquared(blockLoc) > maxDist) {
                    flag("Sign Edit too far", "PacketPlayInUpdateSign");
                    return false;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!data.getPlayer().isOnline()) return;

                    if (!blockLoc.getWorld().isChunkLoaded(blockLoc.getBlockX() >> 4, blockLoc.getBlockZ() >> 4)) {
                        return;
                    }

                    Block block = blockLoc.getBlock();
                    if (!block.getType().name().contains("SIGN")) {
                        flag("Sign Edit on Non-Sign Block", "PacketPlayInUpdateSign");
                        data.getPlayer().sendBlockChange(blockLoc, block.getBlockData());
                    }
                });

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

                    if (blockJson) {
                        String trimmed = line.trim();

                        if (JSON_EXPLIOT_PATTERN.matcher(line).find()) {
                            flag("Dangerous JSON Component", "PacketPlayInUpdateSign");
                            return false;
                        }

                        if (trimmed.startsWith("{") && (trimmed.contains("\":") || trimmed.contains("':"))) {
                            flag("JSON Syntax Detected", "PacketPlayInUpdateSign");
                            return false;
                        }
                    }
                }

            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}