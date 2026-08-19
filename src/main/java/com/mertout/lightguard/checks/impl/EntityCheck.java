package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;

public class EntityCheck extends Check {

    private final boolean preventArmorStandSpam;
    private final long minAttackIntervalMs;

    public EntityCheck(LightGuard plugin) {
        super(plugin);
        this.preventArmorStandSpam = plugin.getConfig().getBoolean("checks.entity.prevent-armorstand-spam", true);
        // Vanilla imposes no click-rate cap. This exists only to catch packet-level automation,
        // so it sits well above what a human can reach by drag-clicking (the old 50ms was 20 CPS).
        this.minAttackIntervalMs = plugin.getConfig().getLong("checks.entity.min-attack-interval-ms", 10);
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
                    if (player.checkAndSetEntityInteractDelay(now, minAttackIntervalMs)) {
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