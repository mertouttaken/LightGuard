package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;

public class EntityCheck extends Check {

    private final boolean preventArmorStandSpam;

    public EntityCheck(LightGuard plugin) {
        super(plugin);
        this.preventArmorStandSpam = plugin.getConfig().getBoolean("checks.entity.prevent-armorstand-spam", true);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundInteractPacket)) return true;

        try {
            if (player.isDead()) {
                return false;
            }

            if (player.isInventoryOpen() && player.getOpenWindowId() > 0) {
                return false;
            }

            if (preventArmorStandSpam) {
                ServerboundInteractPacket interactPacket = (ServerboundInteractPacket) packet;

                if (interactPacket.isAttack()) {
                    long now = System.currentTimeMillis();
                    if (player.checkAndSetEntityInteractDelay(now, 50)) {
                        return false;
                    }
                }
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}