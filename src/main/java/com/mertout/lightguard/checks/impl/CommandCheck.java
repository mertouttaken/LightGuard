package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInChat;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

public class CommandCheck extends Check {

    private static final VarHandle MSG_FIELD;
    private static final Pattern STRIP_PATTERN = Pattern.compile("[^\\p{ASCII}]");

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInChat.class, MethodHandles.lookup());
            MSG_FIELD = lookup.findVarHandle(PacketPlayInChat.class, "a", String.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private final boolean blockSyntax;
    private final List<String> blacklist;

    public CommandCheck(LightGuard plugin) {
        super(plugin);
        this.blockSyntax = plugin.getConfig().getBoolean("checks.command.block-syntax");
        this.blacklist = plugin.getConfig().getStringList("checks.command.blacklist");
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInChat)) return true;

        String msg = (String) MSG_FIELD.get(packet);
        if (msg == null) return true;

        if (msg.length() > 256) {
            plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Chat Message");
            return false;
        }

        if (msg.startsWith("/")) {
            String rawCmd = msg.split(" ")[0].toLowerCase();

            String normalizedCmd = Normalizer.normalize(rawCmd, Normalizer.Form.NFKC);
            normalizedCmd = STRIP_PATTERN.matcher(normalizedCmd).replaceAll("");

            String cleanCmd = normalizedCmd
                    .replaceAll("^/+(minecraft|bukkit|spigot):", "/")
                    .replaceAll("^//+", "/");

            for (String b : blacklist) {
                if (rawCmd.equalsIgnoreCase(b) || normalizedCmd.equalsIgnoreCase(b) || cleanCmd.equalsIgnoreCase(b)) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Blacklisted Command Executed");
                    return false;
                }
            }

            if (containsLog4Shell(rawCmd) || containsLog4Shell(cleanCmd)) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Command Syntax (Log4j)");
                return false;
            }

            if (blockSyntax && (msg.contains("::") || (msg.startsWith("//") && !msg.startsWith("//calc")))) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Command Syntax");
                return false;
            }
        }

        return true;
    }

    private boolean containsLog4Shell(String msg) {
        String lower = msg.toLowerCase();
        return lower.contains("${jndi:") ||
                lower.contains("${ldap:") ||
                lower.contains("${rmi:") ||
                lower.contains("${dns:");
    }
}