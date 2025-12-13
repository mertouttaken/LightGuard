package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInResourcePackStatus;

public class ResourcePackCheck extends Check {

    public ResourcePackCheck(PlayerData data) {
        super(data, "ResourcePack", "resource-pack");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInResourcePackStatus) {
            PacketPlayInResourcePackStatus statusPacket = (PacketPlayInResourcePackStatus) packet;

            if (statusPacket.status == null) {
                flag("Invalid Resource Pack Status", "PacketPlayInResourcePackStatus");
                return false;
            }
        }
        return true;
    }
}