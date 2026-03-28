package com.mertout.lightguard.core;

import com.mertout.lightguard.LightGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ActionQueue {

    private final LightGuard plugin;

    private final Queue<Action> queue = new ConcurrentLinkedQueue<>();

    public ActionQueue(LightGuard plugin) {
        this.plugin = plugin;
        startConsumerTask();
    }

    public void pushKick(Player player, String reason) {
        queue.offer(new Action(player, reason, ActionType.KICK));
    }

    private void startConsumerTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int processed = 0;
            Action action;

            while ((action = queue.poll()) != null && processed < 50) {
                if (action.type == ActionType.KICK) {
                    if (action.player.isOnline()) {
                        action.player.kickPlayer("§cLightGuard Security:\n" + action.reason);
                    }
                }
                processed++;
            }
        }, 1L, 1L);
    }

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