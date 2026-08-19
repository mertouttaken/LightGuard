package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;

import java.text.Normalizer;

public class CommandCheck extends Check {

    private final boolean blockSyntax;
    private final String[] blacklistArray;

    public CommandCheck(LightGuard plugin) {
        super(plugin);
        this.blockSyntax = plugin.getConfig().getBoolean("checks.command.block-syntax");
        this.blacklistArray = plugin.getConfig().getStringList("checks.command.blacklist").toArray(new String[0]);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        String rawMsg = null;
        boolean isCommand = false;

        if (packet instanceof ServerboundChatCommandPacket) {
            rawMsg = ((ServerboundChatCommandPacket) packet).command();
            isCommand = true;
        } else if (packet instanceof ServerboundChatPacket) {
            rawMsg = ((ServerboundChatPacket) packet).message();
            if (rawMsg.startsWith("/")) {
                isCommand = true;
                rawMsg = rawMsg.substring(1);
            }
        } else {
            return true;
        }

        try {
            if (rawMsg == null) return true;
            if (rawMsg.length() > 256) return false;

            if (isCommand) {
                int spaceIndex = rawMsg.indexOf(' ');
                String rawCmd = (spaceIndex == -1 ? rawMsg : rawMsg.substring(0, spaceIndex)).toLowerCase();

                String normalizedCmd = Normalizer.normalize(rawCmd, Normalizer.Form.NFKC);

                StringBuilder strippedCmdBuilder = new StringBuilder();
                for (int i = 0; i < normalizedCmd.length(); i++) {
                    char c = normalizedCmd.charAt(i);
                    if (c <= 127) {
                        strippedCmdBuilder.append(c);
                    }
                }
                String strippedCmd = strippedCmdBuilder.toString();

                String cleanCmd = strippedCmd;
                if (cleanCmd.startsWith("minecraft:")) {
                    cleanCmd = cleanCmd.substring(10);
                } else if (cleanCmd.startsWith("bukkit:")) {
                    cleanCmd = cleanCmd.substring(7);
                } else if (cleanCmd.startsWith("spigot:")) {
                    cleanCmd = cleanCmd.substring(7);
                }

                while (cleanCmd.startsWith("/")) {
                    cleanCmd = cleanCmd.substring(1);
                }

                for (String b : blacklistArray) {
                    String target = b.startsWith("/") ? b.substring(1) : b;
                    if (rawCmd.equalsIgnoreCase(target) || strippedCmd.equalsIgnoreCase(target) || cleanCmd.equalsIgnoreCase(target)) {
                        return false;
                    }
                }

                if (containsLog4Shell(rawMsg) || containsLog4Shell(cleanCmd)) {
                    return false;
                }

                if (blockSyntax) {
                    if (rawMsg.contains("::")) return false;
                    if (rawMsg.startsWith("/") && !rawMsg.startsWith("/calc")) return false;
                }
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
                lower.contains("${rmi:") || lower.contains("${dns:");
    }
}