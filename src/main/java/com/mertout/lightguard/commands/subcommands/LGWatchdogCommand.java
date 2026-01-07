package com.mertout.lightguard.commands.subcommands;
import com.mertout.lightguard.LightGuard;
import org.bukkit.command.CommandSender;

public class LGWatchdogCommand {
    private final LightGuard plugin;
    public LGWatchdogCommand(LightGuard plugin) { this.plugin = plugin; }
    public void execute(CommandSender sender) {
        boolean curr = plugin.getPacketLoggerManager().getWatchdog().isActive();
        plugin.getPacketLoggerManager().getWatchdog().setEnabled(!curr);
        sender.sendMessage("Watchdog: " + (!curr ? "Enabled" : "Disabled"));
    }
}