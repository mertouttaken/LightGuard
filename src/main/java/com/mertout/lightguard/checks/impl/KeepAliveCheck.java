package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInKeepAlive;
import java.lang.reflect.Field;

public class KeepAliveCheck extends Check {

    private static Field packetIdField;

    static {
        try {
            packetIdField = PacketPlayInKeepAlive.class.getDeclaredField("a");
            packetIdField.setAccessible(true);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public KeepAliveCheck(PlayerData data) {
        super(data, "KeepAlive", "bad-packets");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInKeepAlive) {
            try {
                long id = packetIdField.getLong(packet);

                if (!data.getPendingKeepAlives().remove(id)) {
                    flag("Invalid KeepAlive ID (Spoofed)", "PacketPlayInKeepAlive");
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}