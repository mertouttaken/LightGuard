package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInSpectate;

public class SpectateCheck extends Check {

    public SpectateCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInSpectate)) return true;

        if (!player.isCreativeOrSpectator()) {
            plugin.getActionQueue().pushKick(player.getPlayer(), "GameMode Spoof (Spectate Packet)");
            return false;
        }
        return true;
    }
}