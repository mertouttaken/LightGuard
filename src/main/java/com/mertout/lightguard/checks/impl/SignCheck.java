package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;

public class SignCheck extends Check {

    /** Lower-case: compared against an already-lower-cased line. */
    private static final String[] JSON_KEYS = {
            "\"text\"", "'text'",
            "\"score\"", "'score'",
            "\"selector\"", "'selector'",
            "\"extra\"", "'extra'",
            "\"translate\"", "'translate'",
            "\"clickevent\"", "'clickevent'",
            "\"hoverevent\"", "'hoverevent'",
            "\"run_command\"", "'run_command'",
            "\"nbt\"", "'nbt'"
    };

    private final int maxLineLength;
    private final boolean blockJson;

    public SignCheck(LightGuard plugin) {
        super(plugin);
        this.maxLineLength = plugin.getConfig().getInt("checks.sign.max-line-length", 45);
        this.blockJson = plugin.getConfig().getBoolean("checks.sign.block-json-syntax");
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundSignUpdatePacket)) return true;

        try {
            ServerboundSignUpdatePacket signPacket = (ServerboundSignUpdatePacket) packet;
            BlockPos pos = signPacket.getPos();

            if (pos == null) return true;

            double pX = player.getLastX();
            double pY = player.getLastY();
            double pZ = player.getLastZ();

            if (player.hasBaseline() && !player.isInTeleportGrace()) {
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

            int chunkX = (int) Math.floor(pos.getX()) >> 4;
            int chunkZ = (int) Math.floor(pos.getZ()) >> 4;

            if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(worldName, chunkX, chunkZ)) {
                return false;
            }

            String[] lines = signPacket.getLines();
            if (lines == null || lines.length != 4) return true;

            for (String line : lines) {
                if (line == null || line.isEmpty()) continue;

                if (line.length() > maxLineLength || line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256) {
                    return false;
                }

                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c < 32 && c != 10 && c != 13) {
                        return false;
                    }
                }

                if (blockJson && containsJsonExploit(line)) {
                    return false;
                }
            }

        } catch (Throwable t) {
            return false;
        }
        return true;
    }

    private boolean containsJsonExploit(String line) {
        String lowerLine = line.toLowerCase();
        for (String key : JSON_KEYS) {
            if (lowerLine.contains(key)) {
                return true;
            }
        }
        return false;
    }
}