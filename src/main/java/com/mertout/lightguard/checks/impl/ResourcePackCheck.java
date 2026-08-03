package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInResourcePackStatus;

public class ResourcePackCheck extends Check {

    public ResourcePackCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInResourcePackStatus)) return true;

        try {
            PacketPlayInResourcePackStatus statusPacket = (PacketPlayInResourcePackStatus) packet;
            if (statusPacket.status == null) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}