package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInKeepAlive;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class KeepAliveCheck extends Check {

    private static final VarHandle ID_FIELD;

    static {
        try {
            ID_FIELD = MethodHandles.privateLookupIn(PacketPlayInKeepAlive.class, MethodHandles.lookup())
                    .findVarHandle(PacketPlayInKeepAlive.class, "a", long.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    public KeepAliveCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInKeepAlive)) return true;

        try {
            long id = (long) ID_FIELD.get(packet);

            player.getKeepAliveTracker().onKeepAliveReceived(id);

        } catch (Throwable t) {
        }

        return true;
    }
}