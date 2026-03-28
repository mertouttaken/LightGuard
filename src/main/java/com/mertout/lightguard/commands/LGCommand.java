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
        if (!sender.hasPermission("lg.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.getConfigManager().reload();
                sender.sendMessage("§a[LightGuard] System reloaded successfully.");
                break;

            case "tps":
                sender.sendMessage("§a[LightGuard] §7Current TPS: §f" + String.format("%.2f", plugin.getTPS()));
                break;

            case "profile":
                LGProfileCommand.toggle(sender);
                break;

            case "benchmark":
                if (args.length > 1 && args[1].equalsIgnoreCase("reset")) {
                    plugin.getPerformanceMonitor().reset();
                    sender.sendMessage("§a[LightGuard] Performance data reset.");
                } else {
                    plugin.getPerformanceMonitor().printStats();
                    sender.sendMessage("§a[LightGuard] Stats printed to console.");
                }
                break;

            case "stats":
                new LGStatsCommand(plugin).execute(sender, args);
                break;

            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage("§8§m----------§r §cLightGuard Command Center §8§m----------");
        sender.sendMessage(" §4/lg reload §8» §fReload configurations");
        sender.sendMessage(" §4/lg stats §8» §fShow security metrics");
        sender.sendMessage(" §4/lg profile §8» §fLive packet monitoring");
        sender.sendMessage(" §4/lg benchmark §8» §fPerformance debug (Console)");
        sender.sendMessage(" §4/lg tps §8» §fInternal server heartbeat");
        sender.sendMessage("§8§m------------------------------------------");
    }
}