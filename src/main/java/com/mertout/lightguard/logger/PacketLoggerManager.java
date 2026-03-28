package com.mertout.lightguard.logger;

import com.mertout.lightguard.LightGuard;
import org.bukkit.Bukkit;

public class PacketLoggerManager {
    private final LightGuard plugin;
    private final PacketLoggerConfig config;
    private final PacketLogWriter writer;

    public PacketLoggerManager(LightGuard plugin) {
        this.plugin = plugin;
        this.config = new PacketLoggerConfig(plugin);
        this.writer = new PacketLogWriter(plugin);
    }

    public void processPacket(String playerName, Object packet, long durationNs) {
        if (!config.isEnabled()) return;

        long threshold = config.getHeavyPacketThreshold();
        if (threshold != -1 && durationNs < threshold) return;

        String packetName = PacketFilter.getPacketName(packet);
        if (!PacketFilter.isPlayerTracked(playerName, config)) return;
        if (!PacketFilter.isAllowed(packetName, config)) return;

        String logLine = "Player: " + playerName + " | Packet: " + packetName + " | Time: " + (durationNs / 1000) + "us";

        PacketLoggerConfig.OutputMode mode = config.getOutputMode();
        if (mode == PacketLoggerConfig.OutputMode.CONSOLE || mode == PacketLoggerConfig.OutputMode.BOTH) {
            plugin.getLogger().info("[Logger] " + logLine);
        }

        if (mode == PacketLoggerConfig.OutputMode.FILE || mode == PacketLoggerConfig.OutputMode.BOTH) {
            writer.log(logLine);
        }
    }

    public void shutdown() { writer.shutdown(); }
    public PacketLoggerConfig getConfig() { return config; }
}