package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;

public class AnvilCheck extends Check {

    public AnvilCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundRenameItemPacket)) return true;

        try {
            if (!player.isAnvilOpen()) {
                return false;
            }

            ServerboundRenameItemPacket renamePacket = (ServerboundRenameItemPacket) packet;
            String newName = renamePacket.getName();

            if (newName == null) return true;

            int len = newName.length();

            if (len > 60) {
                return false;
            }

            boolean isBlank = true;
            for (int i = 0; i < len; i++) {
                if (newName.charAt(i) > 0x20) {
                    isBlank = false;
                    break;
                }
            }
            if (len > 0 && isBlank) {
                return false;
            }

            if (containsLog4Shell(newName)) {
                return false;
            }

            boolean hasColor = false;
            for (int i = 0; i < len; i++) {
                char c = newName.charAt(i);

                // Only the section sign actually formats text; "&" is an ordinary character
                // that appears in plenty of legitimate item names.
                if (c == '§') {
                    hasColor = true;
                }

                if (c < 0x20 && c != 0) {
                    return false;
                }
            }

            if (hasColor && !player.isAdmin()) {
                return false;
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }

    private boolean containsLog4Shell(String msg) {
        if (msg == null || msg.isEmpty()) return false;
        String lower = msg.toLowerCase();
        return lower.contains("${jndi:") || lower.contains("${ldap:") ||
                lower.contains("${rmi:")  || lower.contains("${dns:");
    }
}