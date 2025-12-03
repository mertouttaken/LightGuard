package com.mertout.lightguard.logger;

import com.mertout.lightguard.LightGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PacketLoggerManager {
    private final LightGuard plugin;
    private final PacketLoggerConfig config;
    private final PacketLogWriter writer;
    private final NettyWatchdogManager watchdog;

    public PacketLoggerManager(LightGuard plugin) {
        this.plugin = plugin;
        this.config = new PacketLoggerConfig(plugin);
        this.writer = new PacketLogWriter(plugin);
        this.watchdog = new NettyWatchdogManager(plugin, config);
    }

    public void processPacket(Player player, Object packet, long durationNs) {
        long threshold = config.getHeavyPacketThreshold();
        if (threshold != -1 && durationNs < threshold) {
            if (!config.isEnabled()) return;
        }

        String packetName = PacketFilter.getPacketName(packet);
        if (!PacketFilter.isAllowed(packetName, config)) return;

        String logLine = String.format("[PacketLogger] Player: %s | Packet: %s | Time: %dns",
                player.getName(), packetName, durationNs);

        if (config.getOutputMode() == PacketLoggerConfig.OutputMode.CONSOLE || config.getOutputMode() == PacketLoggerConfig.OutputMode.BOTH) {
            Bukkit.getConsoleSender().sendMessage("§e" + logLine);
        }
        if (config.getOutputMode() == PacketLoggerConfig.OutputMode.FILE || config.getOutputMode() == PacketLoggerConfig.OutputMode.BOTH) {
            writer.log(logLine);
        }
    }

    public void shutdown() {
        watchdog.setEnabled(false);
        writer.shutdown();
    }

    public NettyWatchdogManager getWatchdog() { return watchdog; }
    public PacketLoggerConfig getConfig() { return config; }
}