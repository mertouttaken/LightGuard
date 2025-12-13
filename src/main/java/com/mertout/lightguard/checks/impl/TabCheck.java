package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInTabComplete;
import java.util.List;

public class TabCheck extends Check {

    public TabCheck(PlayerData data) {
        super(data, "Tab");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.tab.enabled")) return true;

        if (packet instanceof PacketPlayInTabComplete) {
            String buffer = getBuffer((PacketPlayInTabComplete) packet);
            String packetName = "PacketPlayInTabComplete"; // Flag için isim

            if (buffer == null) return true;

            // 1. Uzunluk Kontrolü
            if (buffer.length() > plugin.getConfig().getInt("checks.tab.max-length", 256)) {
                flag("Oversized Tab Request (" + buffer.length() + ")", packetName);
                return false;
            }

            // 2. Yasaklı Karakterler (Crash/Exploit)
            List<String> blockedChars = plugin.getConfig().getStringList("checks.tab.block-chars");
            for (String s : blockedChars) {
                if (buffer.contains(s)) {
                    flag("Illegal Character in Tab", packetName);
                    return false;
                }
            }

            // 3. Advanced Filter (Kelime Bazlı Exploit)
            List<String> blockedSubstrings = plugin.getConfig().getStringList("checks.tab.block-contains");
            String lowerBuffer = buffer.toLowerCase();
            for (String s : blockedSubstrings) {
                if (lowerBuffer.contains(s.toLowerCase())) {
                    flag("Malicious Syntax in Tab (" + s + ")", packetName);
                    return false;
                }
            }

            // ➤ 4. GİZLİ KOMUT ENGELLEME (YENİ)
            // Sunucu bilgilerini sızdıran komutların tab tamamlamasını engeller.
            List<String> blacklistedCmds = plugin.getConfig().getStringList("checks.tab.blacklisted-commands");
            for (String cmd : blacklistedCmds) {
                // Eğer oyuncu "/ver" yazıp tab'a bastıysa ve listede "/ver" varsa
                if (lowerBuffer.startsWith(cmd.toLowerCase())) {
                    // Flag atmaya gerek yok, sadece tamamlamayı iptal et (Sessiz koruma)
                    return false;
                }
            }
        }
        return true;
    }

    private String getBuffer(PacketPlayInTabComplete p) {
        try {
            java.lang.reflect.Field f = p.getClass().getDeclaredField("a");
            f.setAccessible(true);
            return (String) f.get(p);
        } catch (Exception e) { return ""; }
    }
}