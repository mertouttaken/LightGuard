package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

public class ResourcePackCheck extends Check {

    public ResourcePackCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundResourcePackPacket)) return true;

        try {
            ServerboundResourcePackPacket statusPacket = (ServerboundResourcePackPacket) packet;
            if (statusPacket.action() == null) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}