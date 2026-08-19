package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;

import java.util.HashSet;
import java.util.Set;

public class PacketSizeCheck extends Check {

    private final Set<String> excludedClasses = new HashSet<>();
    private final int maxStringLength;

    public PacketSizeCheck(LightGuard plugin) {
        super(plugin);
        this.maxStringLength = plugin.getConfig().getInt("checks.packet-size.max-string-length", 256);

        this.excludedClasses.addAll(plugin.getConfig().getStringList("checks.packet-size.excluded-packets"));
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (excludedClasses.contains(packet.getClass().getSimpleName())) return true;

        try {
            if (packet instanceof ServerboundChatPacket) {
                String msg = ((ServerboundChatPacket) packet).message();
                if (msg != null && msg.length() > maxStringLength) return false;
            }
            else if (packet instanceof ServerboundChatCommandPacket) {
                String cmd = ((ServerboundChatCommandPacket) packet).command();
                if (cmd != null && cmd.length() > maxStringLength) return false;
            }
            else if (packet instanceof ServerboundChatCommandSignedPacket) {
                String cmd = ((ServerboundChatCommandSignedPacket) packet).command();
                if (cmd != null && cmd.length() > maxStringLength) return false;
            }
            else if (packet instanceof ServerboundCommandSuggestionPacket) {
                String text = ((ServerboundCommandSuggestionPacket) packet).getCommand();
                if (text != null && text.length() > 256) return false;
            }
            else if (packet instanceof ServerboundSignUpdatePacket) {
                String[] lines = ((ServerboundSignUpdatePacket) packet).getLines();
                if (lines != null) {
                    int totalChars = 0;
                    for (String line : lines) {
                        if (line != null) totalChars += line.length();
                    }
                    if (totalChars > 1000) return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }
        return true;
    }
}