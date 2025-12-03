package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInResourcePackStatus;

public class ResourcePackCheck extends Check {

    public ResourcePackCheck(PlayerData data) {
        super(data, "ResourcePack");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.resource-pack.enabled")) return true;

        if (packet instanceof PacketPlayInResourcePackStatus) {
            // Bu paket aslında sadece bir ENUM (Status) taşır.
            // Ancak bazı crash clientler bu paketi modifiye edip hatalı veri yollayabilir.

            PacketPlayInResourcePackStatus statusPacket = (PacketPlayInResourcePackStatus) packet;

            // Eğer status null ise veya enum değeri bozuksa
            if (statusPacket.status == null) {
                flag("Invalid Resource Pack Status");
                return false;
            }

            // Flood koruması (Aynı anda 50 tane status yollarsa)
            // Bunu FloodCheck içinde hallettik ama buraya da ekleyebiliriz.
        }
        return true;
    }
}