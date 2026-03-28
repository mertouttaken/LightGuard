package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInSettings;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Arrays;

public class SettingsCheck extends Check {

    private static final VarHandle LOCALE_HANDLE;
    private static final VarHandle VIEW_DIST_HANDLE;

    static {
        VarHandle locale = null;
        VarHandle viewDist = null;
        try {
            Field localeField = Arrays.stream(PacketPlayInSettings.class.getDeclaredFields())
                    .filter(f -> f.getType() == String.class)
                    .findFirst()
                    .orElse(null);

            Field viewDistField = Arrays.stream(PacketPlayInSettings.class.getDeclaredFields())
                    .filter(f -> f.getType() == int.class)
                    .findFirst()
                    .orElse(null);

            if (localeField != null) {
                localeField.setAccessible(true);
                locale = MethodHandles.lookup().unreflectVarHandle(localeField);
            }

            if (viewDistField != null) {
                viewDistField.setAccessible(true);
                viewDist = MethodHandles.lookup().unreflectVarHandle(viewDistField);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        LOCALE_HANDLE = locale;
        VIEW_DIST_HANDLE = viewDist;
    }

    private final int maxLength;

    public SettingsCheck(LightGuard plugin) {
        super(plugin);
        this.maxLength = plugin.getConfig().getInt("checks.client-settings.max-length", 16);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInSettings)) return true;

        if (LOCALE_HANDLE == null) return true;

        try {
            String locale = (String) LOCALE_HANDLE.get(packet);

            if (locale == null || locale.length() > maxLength) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Language Locale");
                return false;
            }

            if (VIEW_DIST_HANDLE != null) {
                int viewDistance = (int) VIEW_DIST_HANDLE.get(packet);
                if (viewDistance < 0 || viewDistance > 32) {
                    return false;
                }
            }

        } catch (Exception e) {
            return false;
        }

        return true;
    }
}