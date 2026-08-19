package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;

public class BadPacketCheck extends Check {

    private final boolean preventSelfInteract;

    public BadPacketCheck(LightGuard plugin) {
        super(plugin);
        this.preventSelfInteract = plugin.getConfig().getBoolean("checks.bad-packets.prevent-self-interact", true);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundInteractPacket)) return true;

        try {
            ServerboundInteractPacket interactPacket = (ServerboundInteractPacket) packet;
            int entityId = interactPacket.getEntityId();

            if (preventSelfInteract && player.getPlayer() != null && entityId == player.getPlayer().getEntityId()) {
                return false;
            }

            final boolean[] vectorInvalid = {false};

            interactPacket.dispatch(new ServerboundInteractPacket.Handler() {
                @Override
                public void onInteraction(net.minecraft.world.InteractionHand hand) {}

                @Override
                public void onAttack() {}

                @Override
                public void onInteraction(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 target) {
                    if (target != null) {
                        if (!Double.isFinite(target.x()) || !Double.isFinite(target.y()) || !Double.isFinite(target.z())) {
                            vectorInvalid[0] = true;
                        }
                        if (Math.abs(target.x()) > 30 || Math.abs(target.y()) > 30 || Math.abs(target.z()) > 30) {
                            vectorInvalid[0] = true;
                        }
                    }
                }
            });

            if (vectorInvalid[0]) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}