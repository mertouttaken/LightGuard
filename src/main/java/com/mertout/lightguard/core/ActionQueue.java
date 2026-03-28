package com.mertout.lightguard.core;

import com.mertout.lightguard.LightGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ActionQueue {

    private final LightGuard plugin;

    // Kilitsiz (Lock-free) hızlı kuyruk. Netty'yi asla yavaşlatmaz.
    private final Queue<Action> queue = new ConcurrentLinkedQueue<>();

    public ActionQueue(LightGuard plugin) {
        this.plugin = plugin;
        startConsumerTask();
    }

    /**
     * Netty Thread'inden çağrılır. İşlemi sadece kuyruğa bırakır ve anında yoluna devam eder.
     */
    public void pushKick(Player player, String reason) {
        queue.offer(new Action(player, reason, ActionType.KICK));
    }

    /**
     * Ana (Main) Thread'de her Tick (50ms) çalışan işleyici.
     */
    private void startConsumerTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int processed = 0;
            Action action;

            // Ana thread'in TPS'ini korumak için 1 Tick'te maksimum 50 işlem (Kick/Log) yapıyoruz.
            // Eğer saldırı çok büyükse kalanlar bir sonraki Tick'e devreder.
            while ((action = queue.poll()) != null && processed < 50) {
                if (action.type == ActionType.KICK) {
                    if (action.player.isOnline()) {
                        action.player.kickPlayer("§cLightGuard Security:\n" + action.reason);
                    }
                }
                processed++;
            }
        }, 1L, 1L); // Her tick (1L) çalışır
    }

    // --- Basit Veri Yapısı ---
    private enum ActionType { KICK }

    private static class Action {
        final Player player;
        final String reason;
        final ActionType type;

        Action(Player player, String reason, ActionType type) {
            this.player = player;
            this.reason = reason;
            this.type = type;
        }
    }
}