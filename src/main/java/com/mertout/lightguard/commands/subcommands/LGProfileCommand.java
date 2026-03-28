package com.mertout.lightguard.commands.subcommands;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
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

                List<ProtocolPlayer> topPlayers = LightGuard.getInstance().getProtocolManager().getPlayers().values().stream()
                        .sorted(Comparator.comparingInt(ProtocolPlayer::getPPS).reversed())
                        .limit(10)
                        .collect(Collectors.toList());

                sender.sendMessage("\n§8§m---------§r §cLightGuard Live Profiler §8§m---------");
                sender.sendMessage("§7(Server TPS: §f" + String.format("%.2f", LightGuard.getInstance().getTPS()) + "§7)");

                if (topPlayers.isEmpty()) {
                    sender.sendMessage("§7No active packet data.");
                } else {
                    for (ProtocolPlayer pp : topPlayers) {
                        int pps = pp.getPPS();
                        String color = "§a";
                        if (pps > 80) color = "§e";
                        if (pps > 150) color = "§c";
                        if (pps > 400) color = "§4§l";

                        sender.sendMessage("§7- " + pp.getPlayer().getName() + ": " + color + pps + " PPS");
                    }
                }
            }
        };

        newTask.runTaskTimer(LightGuard.getInstance(), 0L, 20L);
        activeTasks.put(senderId, newTask);
    }

    public static void cancelAll() {
        activeTasks.values().forEach(task -> { if (!task.isCancelled()) task.cancel(); });
        activeTasks.clear();
    }
}