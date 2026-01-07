package com.mertout.lightguard.commands;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.commands.subcommands.LGProfileCommand;
import com.mertout.lightguard.commands.subcommands.LGStatsCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class LGCommand implements CommandExecutor {
    private final LightGuard plugin;

    public LGCommand(LightGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Permission Check
        if (!sender.hasPermission("lg.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        // Show help if no arguments
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // Subcommands managed via switch
        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.getConfigManager().reload();
                sender.sendMessage("§a[LightGuard] Configuration and caches have been reloaded.");
                break;

            case "tps":
                sender.sendMessage("§8[§bLightGuard§8] §7Server TPS: §f" + String.format("%.2f", plugin.getTPS()));
                break;

            case "profile":
                LGProfileCommand.toggle(sender);
                break;

            case "benchmark":
                if (args.length > 1 && args[1].equalsIgnoreCase("reset"))
                {
                    plugin.getPerformanceMonitor().reset();
                    sender.sendMessage("§a[LightGuard] Benchmark data has been reset.");
                }
                else
                {
                    sender.sendMessage("§a[LightGuard] Printing benchmark statistics to console...");
                    plugin.getPerformanceMonitor().printStats();
                }
                break;

            case "stats":
                if (plugin.getMetrics() != null) {
                    new LGStatsCommand(plugin).execute(sender, args);
                } else {
                    sender.sendMessage("§cStatistics system is disabled.");
                }
                break;

            default:
                sender.sendMessage("§c[LightGuard] Unknown command.");
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage("§8§m----------§r §cLightGuard Admin Help §8§m----------");
        sender.sendMessage(" §4/lg reload §8- §cReloads the configuration.");
        sender.sendMessage(" §4/lg stats <reset> §8- §cDisplays security statistics.");
        sender.sendMessage(" §4/lg profile §8- §cToggles the live packet profiler.");
        sender.sendMessage(" §4/lg tps §8- §cDisplays current server TPS.");
        sender.sendMessage(" §4/lg benchmark <reset> §8- §cPrints performance tests to console.");
        sender.sendMessage("§8§m------------------------------------------");
    }
}