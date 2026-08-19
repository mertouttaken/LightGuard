package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;

public class KeepAliveCheck extends Check {

    public KeepAliveCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundKeepAlivePacket)) return true;

        try {
            ServerboundKeepAlivePacket keepAlivePacket = (ServerboundKeepAlivePacket) packet;
            long id = keepAlivePacket.getId();

            if (!player.getKeepAliveTracker().onKeepAliveReceived(id)) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}