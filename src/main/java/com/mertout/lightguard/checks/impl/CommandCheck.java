package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInChat;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.text.Normalizer;
import java.util.regex.Pattern;

public class CommandCheck extends Check {

    private static final VarHandle MSG_FIELD;
    private static final Pattern STRIP_PATTERN = Pattern.compile("[^\\p{ASCII}]");

    static {
        VarHandle msg = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInChat.class, MethodHandles.lookup());
            for (Field field : PacketPlayInChat.class.getDeclaredFields()) {
                if (field.getType() == String.class) {
                    field.setAccessible(true);
                    msg = lookup.unreflectVarHandle(field);
                    break;
                }
            }
        } catch (Exception ignored) {}
        MSG_FIELD = msg;
    }

    private final boolean blockSyntax;
    private final String[] blacklistArray;

    public CommandCheck(LightGuard plugin) {
        super(plugin);
        this.blockSyntax = plugin.getConfig().getBoolean("checks.command.block-syntax");
        this.blacklistArray = plugin.getConfig().getStringList("checks.command.blacklist").toArray(new String[0]);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInChat)) return true;

        try {
            if (MSG_FIELD == null) return true;

            String msg = (String) MSG_FIELD.get(packet);
            if (msg == null) return true;

            if (msg.length() > 256) {
                return false;
            }

            if (msg.startsWith("/")) {
                int spaceIndex = msg.indexOf(' ');
                String rawCmd = (spaceIndex == -1 ? msg : msg.substring(0, spaceIndex)).toLowerCase();

                String normalizedCmd = Normalizer.normalize(rawCmd, Normalizer.Form.NFKC);
                normalizedCmd = STRIP_PATTERN.matcher(normalizedCmd).replaceAll("");

                String cleanCmd = normalizedCmd
                        .replaceAll("^/+(minecraft|bukkit|spigot):", "/")
                        .replaceAll("^//+", "/");

                for (String b : blacklistArray) {
                    if (rawCmd.equalsIgnoreCase(b) || normalizedCmd.equalsIgnoreCase(b) || cleanCmd.equalsIgnoreCase(b)) {
                        return false;
                    }
                }

                if (containsLog4Shell(rawCmd) || containsLog4Shell(cleanCmd)) {
                    return false;
                }

                if (blockSyntax && (msg.contains("::") || (msg.startsWith("//") && !msg.startsWith("//calc")))) {
                    return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }

    private boolean containsLog4Shell(String msg) {
        if (msg == null || msg.isEmpty()) return false;
        return msg.contains("${jndi:") || msg.contains("${JNDI:") ||
                msg.contains("${ldap:") || msg.contains("${LDAP:") ||
                msg.contains("${rmi:")  || msg.contains("${RMI:")  ||
                msg.contains("${dns:")  || msg.contains("${DNS:");
    }
}