package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInAbilities;

public class AbilitiesCheck extends Check {

    public AbilitiesCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInAbilities)) return true;

        PacketPlayInAbilities abilities = (PacketPlayInAbilities) packet;

        // 🛡️ DÜZELTME: Bukkit API (GameMode) yerine ProtocolPlayer'daki Netty Cache'i okuyoruz.
        if (!player.isCreativeOrSpectator()) {

            // Yine Bukkit API (getAllowFlight) yerine ProtocolPlayer Cache'i!
            if (abilities.isFlying() && !player.isAllowedFlight()) {

                // MPSC Kuyruğu ile Lock-Free kick!
                plugin.getActionQueue().pushKick(player.getPlayer(), "Illegal Flying State (Spoof)");
                return false;
            }
        }
        return true;
    }
}