package com.mertout.lightguard.commands.subcommands;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class LGProfileCommand {

    private static final Map<UUID, BukkitRunnable> activeTasks = new ConcurrentHashMap<>();

    public static void toggle(CommandSender sender) {
        UUID senderId = (sender instanceof Player) ? ((Player) sender).getUniqueId() : new UUID(0, 0);

        BukkitRunnable existingTask = activeTasks.get(senderId);

        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
            activeTasks.remove(senderId);
            sender.sendMessage("§c[LightGuard] Profiler Stopped.");
            return;
        }

        sender.sendMessage("§a[LightGuard] Profiler Started.");

        BukkitRunnable newTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (sender instanceof Player && !((Player) sender).isOnline()) {
                    this.cancel();
                    activeTasks.remove(senderId);
                    return;
                }

                List<PlayerData> topPlayers = Bukkit.getOnlinePlayers().stream()
                        .map(p -> LightGuard.getInstance().getPlayerDataManager().getData(p.getUniqueId()))
                        .filter(data -> data != null)
                        .sorted(Comparator.comparingInt(PlayerData::getPPS).reversed())
                        .limit(10)
                        .collect(Collectors.toList());

                sender.sendMessage("\n§8§m---------§r §cLightGuard Live Profiler §8§m---------");
                sender.sendMessage("§7(Server TPS: §f" + String.format("%.2f", LightGuard.getInstance().getTPS()) + "§7)");

                if (topPlayers.isEmpty()) {
                    sender.sendMessage("§7No data or no player.");
                } else {
                    for (PlayerData data : topPlayers) {
                        int pps = data.getPPS();
                        String color = "§a";
                        if (pps > 50) color = "§e";
                        if (pps > 100) color = "§c";
                        if (pps > 300) color = "§4§l";

                        sender.sendMessage("§7- " + data.getPlayer().getName() + ": " + color + pps + " PPS");
                    }
                }
            }
        };

        newTask.runTaskTimer(LightGuard.getInstance(), 0L, 40L);
        activeTasks.put(senderId, newTask);
    }
}