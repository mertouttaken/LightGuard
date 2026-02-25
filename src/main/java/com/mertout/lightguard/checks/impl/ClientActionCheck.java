package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInClientCommand;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class ClientActionCheck extends Check {

    private static final VarHandle ACTION_FIELD;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInClientCommand.class, MethodHandles.lookup());
            ACTION_FIELD = lookup.findVarHandle(PacketPlayInClientCommand.class, "a", PacketPlayInClientCommand.EnumClientCommand.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private long lastRespawnTime = 0;
    private int respawnCount = 0;

    public ClientActionCheck(PlayerData data) {
        super(data, "ClientAction", "bad-packets");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInClientCommand) {
            try {
                PacketPlayInClientCommand.EnumClientCommand action = (PacketPlayInClientCommand.EnumClientCommand) ACTION_FIELD.get(packet);

                if (action == PacketPlayInClientCommand.EnumClientCommand.PERFORM_RESPAWN) {
                    long now = System.currentTimeMillis();

                    if (now - lastRespawnTime > 1000) {
                        respawnCount = 0;
                        lastRespawnTime = now;
                    }

                    respawnCount++;

                    if (respawnCount > 4) {
                        flag("Respawn Packet Spam (" + respawnCount + "/s)", "PacketPlayInClientCommand");
                        return false;
                    }
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}