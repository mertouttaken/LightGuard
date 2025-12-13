package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInItemName;
import org.bukkit.event.inventory.InventoryType;

import java.lang.reflect.Field;

public class AnvilCheck extends Check {

    private static Field nameField;

    static {
        try {
            // PacketPlayInItemName -> 'a' (String: Yeni isim)
            nameField = PacketPlayInItemName.class.getDeclaredField("a");
            nameField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public AnvilCheck(PlayerData data) {
        super(data, "Anvil");
    }

    @Override
    public boolean check(Object packet) {
        // Config kontrolü (eklemeyi unutma)
        if (!plugin.getConfig().getBoolean("checks.anvil.enabled", true)) return true;

        if (packet instanceof PacketPlayInItemName) {
            try {
                // Oyuncu şu an gerçekten örste mi?
                if (data.getPlayer().getOpenInventory().getType() != InventoryType.ANVIL) {
                    flag("Illegal Item Rename (No Anvil)", "PacketPlayInItemName");
                    return false;
                }

                String newName = (String) nameField.get(packet);

                if (newName == null) return true;

                // 1. Uzunluk Limiti (Normalde 35-50 arasıdır, 60 güvenli sınır)
                if (newName.length() > 60) {
                    flag("Oversized Anvil Rename (" + newName.length() + ")", "PacketPlayInItemName");
                    return false;
                }

                // 2. Illegal Karakterler (Renkli isim vb. yetkisi yoksa)
                if (!data.getPlayer().hasPermission("lightguard.bypass.anvil")) {
                    if (newName.contains("§") || newName.contains("&")) {
                        flag("Illegal Color Codes in Anvil", "PacketPlayInItemName");
                        return false;
                    }
                }

                // 3. Geçersiz Karakterler (Crash)
                // ChatSecurityCheck'teki regex'i burada da kullanabilirsin
                if (newName.chars().anyMatch(c -> c < 0x20 && c != 0)) { // Control characters
                    flag("Invalid Characters in Anvil", "PacketPlayInItemName");
                    return false;
                }

            } catch (Exception e) {
                // Reflection hatası
            }
        }
        return true;
    }
}