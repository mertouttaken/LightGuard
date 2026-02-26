package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInItemName;
import org.bukkit.entity.Player;
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
                Player player = data.getPlayer();

                if (player.getOpenInventory().getType() != InventoryType.ANVIL) {
                    flag("Ghost Anvil Payload (Rename without Anvil GUI)", "PacketPlayInItemName");
                    return false;
                }

                String newName = (String) NAME_FIELD.get(packet);
                if (newName == null) return true;

                if (newName.length() > 0 && newName.trim().isEmpty()) {
                    flag("Blank Item Name Exploit", "PacketPlayInItemName");
                    return false;
                }

                if (containsLog4Shell(newName)) {
                    flag("Invalid Command Syntax (Log4j) in Anvil", "PacketPlayInItemName");
                    return false;
                }

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

            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private boolean containsLog4Shell(String msg) {
        String lower = msg.toLowerCase();
        return lower.contains("${jndi:") ||
                lower.contains("${ldap:") ||
                lower.contains("${rmi:") ||
                lower.contains("${dns:");
    }
}