package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInClientCommand;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class ClientActionCheck extends Check {

    private static final VarHandle ACTION_FIELD;

    static {
        VarHandle action = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInClientCommand.class, MethodHandles.lookup());
            for (Field field : PacketPlayInClientCommand.class.getDeclaredFields()) {
                if (field.getType() == PacketPlayInClientCommand.EnumClientCommand.class) {
                    field.setAccessible(true);
                    action = lookup.unreflectVarHandle(field);
                    break;
                }
            }
        } catch (Exception ignored) {}
        ACTION_FIELD = action;
    }

    public ClientActionCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInClientCommand)) return true;

        try {
            if (ACTION_FIELD == null) return true;

            PacketPlayInClientCommand.EnumClientCommand action = (PacketPlayInClientCommand.EnumClientCommand) ACTION_FIELD.get(packet);

            if (action == PacketPlayInClientCommand.EnumClientCommand.PERFORM_RESPAWN) {
                long now = System.currentTimeMillis();

                if (player.incrementAndGetRespawnCount(now) > 4) {
                    return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}