package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInItemName;
import org.bukkit.event.inventory.InventoryType;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class AnvilCheck extends Check {

    private static final VarHandle NAME_FIELD;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInItemName.class, MethodHandles.lookup());
            NAME_FIELD = lookup.findVarHandle(PacketPlayInItemName.class, "a", String.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public AnvilCheck(PlayerData data) {
        super(data, "Anvil", "anvil");
    }

    @Override
    public boolean check(Object packet) {
        if (!isEnabled()) return true;

        if (packet instanceof PacketPlayInItemName) {
            try {
                if (data.getPlayer().getOpenInventory().getType() != InventoryType.ANVIL) {
                    flag("Illegal Item Rename (No Anvil)", "PacketPlayInItemName");
                    return false;
                }

                String newName = (String) NAME_FIELD.get(packet);
                if (newName == null) return true;

                if (newName.length() > 60) {
                    flag("Oversized Anvil Rename (" + newName.length() + ")", "PacketPlayInItemName");
                    return false;
                }

                if (!data.getPlayer().hasPermission("lightguard.bypass.anvil")) {
                    if (newName.contains("§") || newName.contains("&")) {
                        flag("Illegal Color Codes in Anvil", "PacketPlayInItemName");
                        return false;
                    }
                }

                if (newName.chars().anyMatch(c -> c < 0x20 && c != 0)) {
                    flag("Invalid Characters in Anvil", "PacketPlayInItemName");
                    return false;
                }

            } catch (Exception e) {}
        }
        return true;
    }
}