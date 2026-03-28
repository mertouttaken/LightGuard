package com.mertout.lightguard.commands.subcommands;

import com.mertout.lightguard.LightGuard;
import org.bukkit.command.CommandSender;

public class LGStatsCommand {

    private final LightGuard plugin;
    public LGStatsCommand(LightGuard plugin) { this.plugin = plugin; }

    public void execute(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            plugin.getMetrics().reset();
            sender.sendMessage("§a[LightGuard] Statistics have been reset.");
            return;
        }
        sender.sendMessage(plugin.getMetrics().getFormattedReport());
    }
}