package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

public class BadPacketCheck extends Check {

    private final boolean preventSelfInteract;

    public BadPacketCheck(LightGuard plugin) {
        super(plugin);
        this.preventSelfInteract = plugin.getConfig().getBoolean("checks.bad-packets.prevent-self-interact", true);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        try {
            if (packet instanceof ServerboundMovePlayerPacket movePacket) {
                if (movePacket.hasRotation()) {
                    float yaw = movePacket.getYRot(0.0F);
                    float pitch = movePacket.getXRot(0.0F);
                    if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90.0001F) {
                        return false;
                    }
                }
                if (movePacket.hasPosition()) {
                    double x = movePacket.getX(0.0D);
                    double y = movePacket.getY(0.0D);
                    double z = movePacket.getZ(0.0D);
                    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                        return false;
                    }
                }
                return true;
            }

            if (packet instanceof ServerboundInteractPacket interactPacket) {
                int entityId = interactPacket.getEntityId();

                if (entityId < 0) {
                    return false;
                }

                if (preventSelfInteract && player.getPlayer() != null && entityId == player.getPlayer().getEntityId()) {
                    return false;
                }

                final boolean[] vectorInvalid = {false};

                interactPacket.dispatch(new ServerboundInteractPacket.Handler() {
                    @Override
                    public void onInteraction(InteractionHand hand) {}

                    @Override
                    public void onAttack() {}

                    @Override
                    public void onInteraction(InteractionHand hand, Vec3 target) {
                        if (target != null) {
                            if (!Double.isFinite(target.x()) || !Double.isFinite(target.y()) || !Double.isFinite(target.z())) {
                                vectorInvalid[0] = true;
                            } else if (Math.abs(target.x()) > 8.0 || Math.abs(target.y()) > 8.0 || Math.abs(target.z()) > 8.0) {
                                vectorInvalid[0] = true;
                            }
                        }
                    }
                });

                return !vectorInvalid[0];
            }
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}