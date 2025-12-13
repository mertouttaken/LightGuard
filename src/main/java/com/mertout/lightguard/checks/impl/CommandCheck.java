package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInChat;
import java.util.List;

public class CommandCheck extends Check {

    private final boolean blockSyntax;
    private final List<String> blacklist;

    public CommandCheck(PlayerData data) {
        super(data, "Command", "command");
        this.blockSyntax = plugin.getConfig().getBoolean("checks.command.block-syntax");
        this.blacklist = plugin.getConfig().getStringList("checks.command.blacklist");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInChat) {
            String msg = getMessage((PacketPlayInChat) packet);
            if (msg == null) return true;

            if (msg.length() > 256) {
                flag("Oversized Chat Message", "Chat");
                return false;
            }
            if (msg.codePointCount(0, msg.length()) > 256) {
                flag("Oversized Unicode Message", "Chat");
                return false;
            }

            if (msg.startsWith("/")) {
                String cmd = msg.split(" ")[0].toLowerCase();

                for (String b : blacklist) {
                    if (cmd.equals(b) || cmd.endsWith(":" + b.replace("/", ""))) {
                        flag("Blacklisted Command", "Chat");
                        return false;
                    }
                }

                if (blockSyntax && (msg.contains("::") || msg.startsWith("//"))) {
                    flag("Invalid Command Syntax", "Chat");
                    return false;
                }
            }
        }
        return true;
    }

    private String getMessage(PacketPlayInChat p) {
        try {
            java.lang.reflect.Field f = p.getClass().getDeclaredField("a");
            f.setAccessible(true);
            return (String) f.get(p);
        } catch (Exception e) { return null; }
    }
}