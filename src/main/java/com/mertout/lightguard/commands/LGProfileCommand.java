package com.mertout.lightguard.commands;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class LGProfileCommand {

    private static BukkitRunnable task;

    public static void toggle(CommandSender sender) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
            sender.sendMessage("§c[LightGuard] Profiler durduruldu.");
            return;
        }

        sender.sendMessage("§a[LightGuard] Profiler başlatıldı!");

        task = new BukkitRunnable() {
            @Override
            public void run() {
                // Oyuncuları PPS değerine göre çoktan aza sırala
                List<PlayerData> topPlayers = Bukkit.getOnlinePlayers().stream()
                        .map(p -> LightGuard.getInstance().getPlayerDataManager().getData(p.getUniqueId()))
                        .filter(data -> data != null)
                        .sorted(Comparator.comparingInt(PlayerData::getPPS).reversed())
                        .limit(10) // İlk 10 kişiyi göster
                        .collect(Collectors.toList());

                sender.sendMessage("\n§8§m---------§r §bLightGuard Live Profiler §8§m---------");
                sender.sendMessage("§7(Sunucu TPS: §f" + String.format("%.2f", LightGuard.getInstance().getTPS()) + "§7)");

                if (topPlayers.isEmpty()) {
                    sender.sendMessage("§7Veri yok veya oyuncu yok.");
                }

                for (PlayerData data : topPlayers) {
                    int pps = data.getPPS();
                    String color = "§a"; // Yeşil (Güvenli)
                    if (pps > 50) color = "§e"; // Sarı (Orta)
                    if (pps > 100) color = "§c"; // Kırmızı (Tehlike)
                    if (pps > 300) color = "§4§l"; // Bordo (CRITICAL)

                    sender.sendMessage("§7- " + data.getPlayer().getName() + ": " + color + pps + " PPS");
                }
            }
        };
        // 40 Tick = 2 Saniye
        task.runTaskTimer(LightGuard.getInstance(), 0L, 40L);
    }
}