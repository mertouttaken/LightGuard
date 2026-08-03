package com.mertout.lightguard.core;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ActionQueue {

    private final LightGuard plugin;
    private final Queue<Action> queue = new ConcurrentLinkedQueue<>();
    private String[] cachedKickLayout;

    public ActionQueue(LightGuard plugin) {
        this.plugin = plugin;
        loadConfig();
        startConsumerTask();
    }

    public void loadConfig() {
        this.cachedKickLayout = plugin.getConfig().getStringList("settings.kick-layout").toArray(new String[0]);
    }

    public void pushKick(Player player, String reason) {
        plugin.getMetrics().recordViolation(player.getName(), reason);
        queue.offer(new Action(player.getUniqueId(), player.getName(), reason, ActionType.KICK));
    }

    private void startConsumerTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Action action;
            while ((action = queue.poll()) != null) {

                Player targetPlayer = Bukkit.getPlayer(action.uuid);

                if (targetPlayer != null && targetPlayer.isOnline()) {
                    StringBuilder sb = new StringBuilder(256);
                    String pingStr = "0";

                    try {
                        ProtocolPlayer pp = plugin.getProtocolManager().get(action.uuid);
                        if (pp != null) {
                            pingStr = String.valueOf(pp.getKeepAliveTracker().getPing());
                        }
                    } catch (Exception ignored) {}

                    for (int i = 0; i < cachedKickLayout.length; i++) {
                        String formatted = cachedKickLayout[i].replace("&", "§")
                                .replace("%player%", action.playerName)
                                .replace("%check%", action.reason)
                                .replace("%ping%", pingStr);

                        sb.append(formatted);
                        if (i < cachedKickLayout.length - 1) {
                            sb.append("\n");
                        }
                    }
                    targetPlayer.kickPlayer(sb.toString());
                }
            }
        }, 1L, 1L);
    }

    private enum ActionType { KICK }

    private static class Action {
        final UUID uuid;
        final String playerName;
        final String reason;
        final ActionType type;

        Action(UUID uuid, String playerName, String reason, ActionType type) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.reason = reason;
            this.type = type;
        }
    }
}