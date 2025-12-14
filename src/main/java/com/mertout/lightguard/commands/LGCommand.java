package com.mertout.lightguard.commands;
import com.mertout.lightguard.LightGuard;
import org.bukkit.command.*;

public class LGCommand implements CommandExecutor {
    private final LightGuard plugin;
    public LGCommand(LightGuard plugin) { this.plugin = plugin; }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("lg.admin")) return true;
        if (args.length != 1) return true;
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().reload();
            sender.sendMessage("§aReloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("watchdog")) {
            new LGWatchdogCommand(plugin).execute(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("tps")) {
            sender.sendMessage("§c§lLIGHT GUARD > TPS: " + plugin.getTPS());
            return true;
        }
        if (args[0].equalsIgnoreCase("profile")) {
            LGProfileCommand.toggle(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("benchmark")) {
            sender.sendMessage("§a[LightGuard] Benchmark statistics are being printed to the console...");
            plugin.getPerformanceMonitor().printStats();
            return true;
        }

        if (args[0].equalsIgnoreCase("resetbench") || args[0].equalsIgnoreCase("resetbenchmark")) {
            plugin.getPerformanceMonitor().reset();
            sender.sendMessage("§a[LightGuard] Benchmark data has been reset..");
            return true;
        }

        sender.sendMessage("§aLightGuard Active.");
        return true;
    }
}