package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInItemName;

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

    public AnvilCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInItemName)) return true;

        try {
            // 🛡️ DÜZELTME: Bukkit (getOpenInventory) çağrısı YASAK! Netty Cache'e (ProtocolPlayer) soruyoruz.
            if (!player.isAnvilOpen()) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Ghost Anvil Payload (Desync)");
                return false;
            }

            String newName = (String) NAME_FIELD.get(packet);
            if (newName == null) return true;

            int len = newName.length();

            // 1. OOM / Allocation Koruması
            if (len > 60) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Oversized Anvil Rename");
                return false;
            }

            // 2. Boş İsim (Blank Exploit) Koruması
            if (len > 0 && newName.trim().isEmpty()) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Blank Item Name Exploit");
                return false;
            }

            // 3. Log4Shell Koruması (Sadece <60 char olduğu için toLowerCase yapmak güvenli)
            if (containsLog4Shell(newName)) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Command Syntax (Log4j)");
                return false;
            }

            boolean hasColor = false;

            // 4. Allocation-Free Karakter Taraması (Crash/Unicode Koruması)
            for (int i = 0; i < len; i++) {
                char c = newName.charAt(i);

                if (c == '§' || c == '&') {
                    hasColor = true;
                }

                if (c < 0x20 && c != 0) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Characters in Anvil");
                    return false;
                }
            }

            // 5. Yetki Kontrolü: Bukkit (hasPermission) YASAK! Netty Cache'e sorulur.
            if (hasColor && !player.isAdmin()) {
                return false; // Yetkisi yoksa sadece paketi çöpe at, adamı kicklemeye gerek yok.
            }

        } catch (Exception e) {
            return false; // Hata durumunda (Fail-safe) paketi yut.
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