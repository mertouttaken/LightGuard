package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInSettings;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class SettingsCheck extends Check {

    private static final VarHandle LOCALE_HANDLE;
    private static final VarHandle VIEW_DIST_HANDLE;

    static {
        VarHandle locale = null;
        VarHandle viewDist = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInSettings.class, MethodHandles.lookup());
            for (Field field : PacketPlayInSettings.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == String.class && locale == null) {
                    locale = lookup.unreflectVarHandle(field);
                } else if (field.getType() == int.class && viewDist == null) {
                    viewDist = lookup.unreflectVarHandle(field);
                }
            }
        } catch (Exception ignored) {}
        LOCALE_HANDLE = locale;
        VIEW_DIST_HANDLE = viewDist;
    }

    private final int maxLength;

    public SettingsCheck(LightGuard plugin) {
        super(plugin);
        this.maxLength = plugin.getConfig().getInt("checks.client-settings.max-length", 128);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInSettings)) return true;

        try {
            if (LOCALE_HANDLE != null) {
                String locale = (String) LOCALE_HANDLE.get(packet);
                if (locale != null && locale.length() > maxLength) {
                    return false;
                }
            }

            if (VIEW_DIST_HANDLE != null) {
                int viewDistance = (int) VIEW_DIST_HANDLE.get(packet);
                if (viewDistance < 0 || viewDistance > 64) {
                    return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}