package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.world.inventory.ClickType;

public class WindowCheck extends Check {

    private final boolean strictSlots;
    private final boolean preventLecternSpam;
    private final boolean preventSwapInGui;

    public WindowCheck(LightGuard plugin) {
        super(plugin);
        this.strictSlots = plugin.getConfig().getBoolean("checks.window.strict-slots", true);
        this.preventLecternSpam = plugin.getConfig().getBoolean("checks.window.prevent-lectern-spam", true);
        this.preventSwapInGui = plugin.getConfig().getBoolean("checks.window.prevent-swap-in-gui", true);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (packet instanceof ServerboundPickItemFromBlockPacket pickPacket) {
            try {
                if (pickPacket.includeData() && !player.isCreativeOrSpectator()) {
                    return false;
                }
                BlockPos pos = pickPacket.pos();
                if (pos != null) {
                    int cx = pos.getX() >> 4;
                    int cz = pos.getZ() >> 4;
                    if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(player.getCurrentWorldName(), cx, cz)) {
                        return false;
                    }
                    if (player.hasBaseline()) {
                        double dx = (pos.getX() + 0.5) - player.getLastX();
                        double dy = (pos.getY() + 0.5) - player.getLastY();
                        double dz = (pos.getZ() + 0.5) - player.getLastZ();
                        if ((dx * dx + dy * dy + dz * dz) > 100.0) {
                            return false;
                        }
                    }
                }
            } catch (Throwable t) {
                return false;
            }
            return true;
        }

        if (packet instanceof ServerboundPickItemFromEntityPacket pickPacket) {
            try {
                if (pickPacket.includeData() && !player.isCreativeOrSpectator()) {
                    return false;
                }
            } catch (Throwable t) {
                return false;
            }
            return true;
        }

        if (packet instanceof ServerboundContainerButtonClickPacket buttonPacket) {
            try {
                if (player.isDead()) return false;
                if (buttonPacket.containerId() <= 0 || buttonPacket.buttonId() < 0 || buttonPacket.buttonId() > 128) {
                    return false;
                }
                if (preventLecternSpam && player.incrementAndGetQuickMoveCount(System.currentTimeMillis()) > 15) {
                    return false;
                }
            } catch (Throwable t) {
                return false;
            }
            return true;
        }

        if (!(packet instanceof ServerboundContainerClickPacket clickPacket)) return true;

        try {
            if (player.isDead()) return false;

            int containerId = clickPacket.getContainerId();
            int slot = clickPacket.getSlotNum();
            int button = clickPacket.getButtonNum();
            ClickType type = clickPacket.getClickType();

            if (type == null || containerId < 0 || containerId > 127) {
                return false;
            }

            if (slot < -1 && slot != -999) {
                return false;
            }

            if (strictSlots) {
                if (containerId == 0 && slot > 45) {
                    return false;
                }
                if (slot > 127) {
                    return false;
                }
            }

            if (type == ClickType.SWAP) {
                if (slot < 0 || (button < 0 || (button > 8 && button != 40))) {
                    return false;
                }
                if (preventSwapInGui && containerId > 0 && button == 40) {
                    return false;
                }
            } else if (type == ClickType.QUICK_MOVE) {
                if (slot < 0) {
                    return false;
                }
                if (player.incrementAndGetQuickMoveCount(System.currentTimeMillis()) > 50) {
                    return false;
                }
            } else if (type == ClickType.CLONE) {
                if (!player.isCreativeOrSpectator()) {
                    return false;
                }
            } else if (type == ClickType.THROW || type == ClickType.PICKUP || type == ClickType.PICKUP_ALL) {
                if (button != 0 && button != 1) {
                    return false;
                }
            } else if (button < 0 || button > 100) {
                return false;
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}