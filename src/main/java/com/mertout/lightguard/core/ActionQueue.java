package com.mertout.lightguard.core;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ActionQueue {

    private final LightGuard plugin;
    private final Queue<Action> queue = new ConcurrentLinkedQueue<>();

    private List<String> cachedKickLayout;

    public ActionQueue(LightGuard plugin) {
        this.plugin = plugin;
        loadConfig();
        startConsumerTask();
    }

    public void loadConfig() {
        this.cachedKickLayout = plugin.getConfig().getStringList("settings.kick-layout");
    }

    public void pushKick(Player player, String reason) {
        queue.offer(new Action(player, reason, ActionType.KICK));
    }

    private void startConsumerTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Action action;
            while ((action = queue.poll()) != null) {
                if (action.player.isOnline()) {
                    StringBuilder sb = new StringBuilder();

                    String pingStr = "0";
                    try {
                        ProtocolPlayer pp = plugin.getProtocolManager().get(action.player.getUniqueId());
                        if (pp != null) {
                            pingStr = String.valueOf(pp.getKeepAliveTracker().getPing());
                        }
                    } catch (Exception ignored) {
                    }

                    for (String line : cachedKickLayout) {
                        sb.append(line.replace("&", "§")
                                        .replace("%player%", action.player.getName())
                                        .replace("%check%", action.reason)
                                        .replace("%ping%", pingStr))
                                .append("\n");
                    }
                    action.player.kickPlayer(sb.toString());
                }
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