package com.mertout.lightguard.logger;

import com.mertout.lightguard.LightGuard;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.List;

public class PacketLoggerConfig {
    private boolean enabled;
    private long heavyPacketThreshold;
    private OutputMode outputMode;
    private FilterMode playerMode;
    private List<String> playerList;
    private FilterMode packetMode;
    private List<String> packetList;

    public PacketLoggerConfig(LightGuard plugin) {
        reload(plugin);
    }

    public void reload(LightGuard plugin) {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("packet-logger.enabled");
        this.heavyPacketThreshold = config.getLong("packet-logger.heavy-packet-threshold");
        this.outputMode = OutputMode.valueOf(config.getString("packet-logger.output", "FILE").toUpperCase());
        this.playerMode = FilterMode.valueOf(config.getString("packet-logger.player-mode", "WHITELIST").toUpperCase());
        this.playerList = config.getStringList("packet-logger.players");
        this.packetMode = FilterMode.valueOf(config.getString("packet-logger.packet-mode", "BLACKLIST").toUpperCase());
        this.packetList = config.getStringList("packet-logger.packets");
    }

    // Getters
    public boolean isEnabled() { return enabled; }
    public long getHeavyPacketThreshold() { return heavyPacketThreshold; }
    public OutputMode getOutputMode() { return outputMode; }
    public FilterMode getPlayerMode() { return playerMode; }
    public List<String> getPlayerList() { return playerList; }
    public FilterMode getPacketMode() { return packetMode; }
    public List<String> getPacketList() { return packetList; }

    public enum OutputMode { CONSOLE, FILE, BOTH }
    public enum FilterMode { WHITELIST, BLACKLIST }
}