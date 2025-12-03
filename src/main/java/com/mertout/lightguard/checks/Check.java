package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;

import java.util.List;
import java.util.stream.Collectors;

public abstract class Check {
    protected final LightGuard plugin;
    protected final PlayerData data;
    protected final String name;

    public Check(PlayerData data, String name) {
        this.plugin = LightGuard.getInstance();
        this.data = data;
        this.name = name;
    }

    public abstract boolean check(Object packet);

    // --- ESKİ METOT (Geriye uyumluluk için kalsın) ---
    protected void flag(String info) {
        flag(info, "UnknownPacket");
    }

    // --- YENİ METOT (Paket ismi alan) ---
    protected void flag(String info, String packetName) {
        // Sentinel Modu
        if(plugin.getConfig().getBoolean("settings.sentinel.enabled") &&
                plugin.getConfig().getBoolean("settings.sentinel.silent-failures")) {
            plugin.getLogger().warning(data.getPlayer().getName() + " failed " + name + " (" + packetName + "): " + info);
            return;
        }

        // Kick İşlemi
        Bukkit.getScheduler().runTask(plugin, () -> {
            String kickMessage = buildKickMessage(packetName);
            data.getPlayer().kickPlayer(kickMessage);
        });

        plugin.getLogger().warning(data.getPlayer().getName() + " flagged " + name + " [" + packetName + "] (" + info + ")");
    }

    private String buildKickMessage(String packetName) {
        List<String> layout = plugin.getConfig().getStringList("settings.kick-layout");

        // Ping al
        int ping = 0;
        try {
            ping = ((CraftPlayer) data.getPlayer()).getHandle().ping;
        } catch (Exception e) {
            ping = -1;
        }

        int finalPing = ping;

        // Mesajı oluştur ve %packet% yerini doldur
        return layout.stream()
                .map(line -> line
                        .replace("%player%", data.getPlayer().getName())
                        .replace("%check%", name)
                        .replace("%packet%", packetName) // <-- BURASI EKLENDİ
                        .replace("%ping%", String.valueOf(finalPing))
                        .replace("&", "§"))
                .collect(Collectors.joining("\n"));
    }
}