package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInChat;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;

public class CommandCheck extends Check {

    private static final VarHandle MSG_FIELD;
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInChat.class, MethodHandles.lookup());
            MSG_FIELD = lookup.findVarHandle(PacketPlayInChat.class, "a", String.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

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
            String msg = (String) MSG_FIELD.get(packet);
            if (msg == null) return true;

            if (msg.length() > 256) {
                flag("Oversized Chat Message", "Chat");
                return false;
            }

            if (msg.startsWith("/")) {
                String cmd = msg.split(" ")[0].toLowerCase();

                String cleanCmd = cmd
                        .replaceAll("^/+(minecraft|bukkit|spigot):", "/")
                        .replaceAll("^//+", "/");

                for (String b : blacklist) {
                    if (cmd.equalsIgnoreCase(b) || cleanCmd.equalsIgnoreCase(b))
                    {
                        flag("Blacklisted Command", "Chat");
                        return false;
                    }
                }

                if (blockSyntax && (msg.contains("::") || (msg.startsWith("//") && !msg.startsWith("//calc")))) {
                    flag("Invalid Command Syntax", "Chat");
                    return false;
                }
            }
        }
        return true;
    }
}