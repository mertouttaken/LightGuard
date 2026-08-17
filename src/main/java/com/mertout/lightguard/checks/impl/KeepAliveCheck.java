package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInKeepAlive;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class KeepAliveCheck extends Check {

    private static final VarHandle ID_FIELD;

    static {
        VarHandle id = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInKeepAlive.class, MethodHandles.lookup());
            for (Field field : PacketPlayInKeepAlive.class.getDeclaredFields()) {
                if (field.getType() == long.class) {
                    field.setAccessible(true);
                    id = lookup.unreflectVarHandle(field);
                    break;
                }
            }
        } catch (Exception ignored) {}
        ID_FIELD = id;
    }

    public KeepAliveCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInKeepAlive)) return true;

        try {
            if (ID_FIELD != null) {
                long id = (long) ID_FIELD.get(packet);

                // An id the server never sent is a spoofed/replayed KeepAlive.
                if (!player.getKeepAliveTracker().onKeepAliveReceived(id)) {
                    return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}