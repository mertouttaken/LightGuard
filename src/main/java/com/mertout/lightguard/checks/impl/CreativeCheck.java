package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public class CreativeCheck extends Check {

    private final int maxStackSize;
    private final int maxLoreTotalLength;

    public CreativeCheck(LightGuard plugin) {
        super(plugin);
        this.maxStackSize = plugin.getConfig().getInt("checks.creative.max-stack-size", 64);
        this.maxLoreTotalLength = plugin.getConfig().getInt("checks.creative.max-lore-total-len", 3000);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof ServerboundSetCreativeModeSlotPacket)) return true;

        try {
            if (!player.isCreativeOrSpectator()) {
                return false;
            }

            ServerboundSetCreativeModeSlotPacket creativePacket = (ServerboundSetCreativeModeSlotPacket) packet;
            int slot = creativePacket.slotNum();
            ItemStack item = creativePacket.itemStack();

            if (slot < -1 || slot > 45) {
                return false;
            }

            if (item == null || item.isEmpty()) {
                return true;
            }

            int count = item.getCount();
            if (count > maxStackSize || count < 1) {
                return false;
            }

            // A: CreativeCheckNBTList - total lore characters length
            if (item.has(DataComponents.LORE)) {
                ItemLore lore = item.get(DataComponents.LORE);
                if (lore != null) {
                    int totalLen = 0;
                    for (Component line : lore.lines()) {
                        totalLen += line.getString().length();
                    }
                    if (totalLen > maxLoreTotalLength) {
                        return false;
                    }
                }
            }

            // A: NBTCheckWorldBorder
            if (item.has(DataComponents.CUSTOM_DATA)) {
                CustomData customData = item.get(DataComponents.CUSTOM_DATA);
                if (customData != null) {
                    CompoundTag tag = customData.copyTag();
                    if (tag.contains("BlockEntityTag")) {
                        CompoundTag beTag = tag.getCompound("BlockEntityTag");
                        if (beTag.contains("x") && beTag.contains("z")) {
                            int x = beTag.getInt("x");
                            int z = beTag.getInt("z");
                            if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
                                return false;
                            }
                            int chunkX = x >> 4;
                            int chunkZ = z >> 4;
                            if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(player.getCurrentWorldName(), chunkX, chunkZ)) {
                                return false;
                            }
                        }
                    }
                }
            }

        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}