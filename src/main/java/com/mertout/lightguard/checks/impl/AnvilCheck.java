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

                int len = newName.length();
                if (len > 60) {
                    flag("Oversized Anvil Rename (" + len + ")", "PacketPlayInItemName");
                    return false;
                }

                boolean hasColor = false;
                boolean hasInvalid = false;

                for (int i = 0; i < len; i++) {
                    char c = newName.charAt(i);

                    if (c == '§' || c == '&') {
                        hasColor = true;
                    }

                    if (c < 0x20 && c != 0) {
                        hasInvalid = true;
                        break;
                    }
                }

                if (hasInvalid) {
                    flag("Invalid Characters in Anvil", "PacketPlayInItemName");
                    return false;
                }

                if (hasColor) {
                    if (!data.getPlayer().hasPermission("lg.admin")) {
                        flag("Illegal Color Codes in Anvil", "PacketPlayInItemName");
                        return false;
                    }
                }

            } catch (Exception e) {}
        }
        return true;
    }
}