package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
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
            Field fLocale = PacketPlayInSettings.class.getDeclaredField("a"); // 1.16.5 'a' field
            fLocale.setAccessible(true);
            LOCALE_GETTER = lookup.unreflectGetter(fLocale);

            Field fViewDistance = PacketPlayInSettings.class.getDeclaredField("b"); // 1.16.5 'b' field
            fViewDistance.setAccessible(true);
            VIEW_DISTANCE_GETTER = lookup.unreflectGetter(fViewDistance);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public SettingsCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInSettings)) return true;

        try {
            String locale = (String) LOCALE_GETTER.invoke(packet);
            int viewDistance = (int) VIEW_DISTANCE_GETTER.invoke(packet);

            // 1. Locale Boyut Kontrolü (OOM Protection)
            if (locale != null && locale.length() > 32) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Locale String");
                return false;
            }

            // 2. Görüş Mesafesi Sınırı (Crasher Protection)
            if (viewDistance < 0 || viewDistance > 128) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid View Distance Exploit");
                return false;
            }

        } catch (Throwable t) {
            return false;
        }
        return true;
    }
}