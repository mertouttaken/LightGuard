package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInAbilities;
import org.bukkit.GameMode;

public class AbilitiesCheck extends Check {

    public AbilitiesCheck(PlayerData data) {
        super(data, "Abilities", "bad-packets");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInAbilities) {
            PacketPlayInAbilities abilities = (PacketPlayInAbilities) packet;

            if (data.getGameMode() == GameMode.SURVIVAL || data.getGameMode() == GameMode.ADVENTURE) {
                if (abilities.isFlying() && !data.getPlayer().getAllowFlight()) {
                    flag("Illegal Flying State (Fly Hack / Spoof)", "PacketPlayInAbilities");
                    return false;
                }
            }
        }
        return true;
    }
}