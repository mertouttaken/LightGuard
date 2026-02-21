package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInKeepAlive;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class KeepAliveCheck extends Check {

    private static final VarHandle ID_FIELD;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInKeepAlive.class, MethodHandles.lookup());
            ID_FIELD = lookup.findVarHandle(PacketPlayInKeepAlive.class, "a", long.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public KeepAliveCheck(PlayerData data) {
        super(data, "KeepAlive", "keep-alive");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInKeepAlive) {
            try {
                long id = (long) ID_FIELD.get(packet);

                if (!data.getPendingKeepAlives().containsKey(id)) {
                    flag("Invalid KeepAlive ID (Spoof)", "KeepAlive");
                    return false;
                }

                long sentTime = data.getPendingKeepAlives().remove(id);
                long ping = System.currentTimeMillis() - sentTime;

                if (ping > 60000) {
                    flag("Extreme Latency / Timeout", "KeepAlive");
                }

                if (data.getPendingKeepAlives().size() > 5) {
                    flag("KeepAlive Hoarding (Ping Spoof)", "KeepAlive");
                    data.cleanOldKeepAlives();
                    return false;
                }

            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}