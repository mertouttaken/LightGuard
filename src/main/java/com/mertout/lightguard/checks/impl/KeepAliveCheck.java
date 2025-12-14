package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInKeepAlive;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class KeepAliveCheck extends Check {

    private static final VarHandle PACKET_ID;
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInKeepAlive.class, MethodHandles.lookup());
            PACKET_ID = lookup.findVarHandle(PacketPlayInKeepAlive.class, "a", long.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    public KeepAliveCheck(PlayerData data) {
        super(data, "KeepAlive", "bad-packets");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInKeepAlive) {
            long id = (long) PACKET_ID.get(packet);

            Long timestamp = data.getPendingKeepAlives().remove(id);

            if (timestamp == null) {
                flag("Invalid KeepAlive ID (Spoofed)", "PacketPlayInKeepAlive");
                return false;
            }

            if (System.currentTimeMillis() - timestamp > 60000) {
                flag("KeepAlive Timeout (Lag Switch?)", "PacketPlayInKeepAlive");
                return false;
            }
        }
        return true;
    }
}