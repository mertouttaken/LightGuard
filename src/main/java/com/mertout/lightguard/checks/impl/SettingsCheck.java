package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInSettings;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public class SettingsCheck extends Check {

    private static final MethodHandle LOCALE_GETTER;
    private static final MethodHandle VIEW_DISTANCE_GETTER;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Field fLocale;
            try {
                fLocale = PacketPlayInSettings.class.getDeclaredField("locale");
            } catch (NoSuchFieldException e) {
                fLocale = PacketPlayInSettings.class.getDeclaredField("a");
            }
            fLocale.setAccessible(true);
            LOCALE_GETTER = lookup.unreflectGetter(fLocale);

            Field fViewDistance;
            try {
                fViewDistance = PacketPlayInSettings.class.getDeclaredField("viewDistance");
            } catch (NoSuchFieldException e) {
                fViewDistance = PacketPlayInSettings.class.getDeclaredField("b");
            }
            fViewDistance.setAccessible(true);
            VIEW_DISTANCE_GETTER = lookup.unreflectGetter(fViewDistance);

        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public SettingsCheck(PlayerData data) {
        super(data, "ClientSettings", "client-settings");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInSettings) {
            try {
                String locale = (String) LOCALE_GETTER.invoke(packet);
                int viewDistance = (int) VIEW_DISTANCE_GETTER.invoke(packet);

                if (locale != null && locale.length() > 32) {
                    flag("Oversized Locale String (" + locale.length() + " chars)", "PacketPlayInSettings");
                    return false;
                }

                if (viewDistance < 0 || viewDistance > 128) {
                    flag("Invalid View Distance Exploit (" + viewDistance + ")", "PacketPlayInSettings");
                    return false;
                }

            } catch (Throwable t) {
                return false;
            }
        }
        return true;
    }
}