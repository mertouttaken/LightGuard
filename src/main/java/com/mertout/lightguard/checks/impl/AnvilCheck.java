package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInItemName;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class AnvilCheck extends Check {

    private static final VarHandle NAME_FIELD;

    static {
        VarHandle name = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInItemName.class, MethodHandles.lookup());
            for (Field field : PacketPlayInItemName.class.getDeclaredFields()) {
                if (field.getType() == String.class) {
                    field.setAccessible(true);
                    name = lookup.unreflectVarHandle(field);
                    break;
                }
            }
        } catch (Exception ignored) {}
        NAME_FIELD = name;
    }

    public AnvilCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInItemName)) return true;

        try {
            if (NAME_FIELD == null) return true;

            if (!player.isAnvilOpen()) {
                return false;
            }

            String newName = (String) NAME_FIELD.get(packet);
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

                if (c == '§' || c == '&') {
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
        return msg.contains("${jndi:") || msg.contains("${JNDI:") ||
                msg.contains("${ldap:") || msg.contains("${LDAP:") ||
                msg.contains("${rmi:")  || msg.contains("${RMI:")  ||
                msg.contains("${dns:")  || msg.contains("${DNS:");
    }
}