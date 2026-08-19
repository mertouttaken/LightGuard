package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;

public class AbilitiesCheck extends Check {

    public AbilitiesCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundPlayerAbilitiesPacket)) return true;

        try {
            ServerboundPlayerAbilitiesPacket abilities = (ServerboundPlayerAbilitiesPacket) packet;

            if (!player.isCreativeOrSpectator()) {
                if (abilities.isFlying() && !player.isAllowedFlight()) {
                    return false;
                }
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}