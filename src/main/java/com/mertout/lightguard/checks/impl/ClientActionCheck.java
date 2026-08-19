package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;

public class ClientActionCheck extends Check {

    public ClientActionCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundClientCommandPacket)) return true;

        try {
            ServerboundClientCommandPacket actionPacket = (ServerboundClientCommandPacket) packet;

            if (actionPacket.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN) {
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