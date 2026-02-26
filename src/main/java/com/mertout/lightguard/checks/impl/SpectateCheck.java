package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInSpectate;
import org.bukkit.GameMode;

public class SpectateCheck extends Check {

    public SpectateCheck(PlayerData data) {
        super(data, "Spectate", "game-state");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInSpectate) {
            if (data.getGameMode() != GameMode.SPECTATOR) {
                flag("GameMode Spoof (Spectate Packet in Survival)", "PacketPlayInSpectate");
                return false;
            }
        }
        return true;
    }
}