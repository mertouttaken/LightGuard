package com.mertout.lightguard.logger;

import com.mertout.lightguard.LightGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PacketLoggerManager {
    private final LightGuard plugin;
    private final PacketLoggerConfig config;
    private final PacketLogWriter writer;

    public PacketLoggerManager(LightGuard plugin) {
        this.plugin = plugin;
        this.config = new PacketLoggerConfig(plugin);
        this.writer = new PacketLogWriter(plugin);
    }

    public void processPacket(Player player, Object packet, long durationNs) {
        long threshold = config.getHeavyPacketThreshold();
        if (threshold != -1 && durationNs < threshold) {
            if (!config.isEnabled()) return;
        }

        String packetName = PacketFilter.getPacketName(packet);
        if (!PacketFilter.isAllowed(packetName, config)) return;

        String logLine = "[PacketLogger] Player: " + player.getName() +
                " | Packet: " + packetName +
                " | Time: " + durationNs + "ns";

        if (config.getOutputMode() == PacketLoggerConfig.OutputMode.CONSOLE || config.getOutputMode() == PacketLoggerConfig.OutputMode.BOTH) {
            Bukkit.getConsoleSender().sendMessage("§e" + logLine);
        }
        if (config.getOutputMode() == PacketLoggerConfig.OutputMode.FILE || config.getOutputMode() == PacketLoggerConfig.OutputMode.BOTH) {
            writer.log(logLine);
        }
    }

    public void shutdown() {
        writer.shutdown();
    }

    public PacketLoggerConfig getConfig() { return config; }
}