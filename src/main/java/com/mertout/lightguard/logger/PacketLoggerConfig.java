package com.mertout.lightguard.logger;

import com.mertout.lightguard.LightGuard;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.HashSet;
import java.util.Set;

public class PacketLoggerConfig {
    private boolean enabled;
    private long heavyPacketThreshold;
    private OutputMode outputMode;
    private Set<String> playerList = new HashSet<>();
    private Set<String> packetList = new HashSet<>();
    private FilterMode playerMode;
    private FilterMode packetMode;

    public PacketLoggerConfig(LightGuard plugin) {
        reload(plugin);
    }

    public void reload(LightGuard plugin) {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("packet-logger.enabled", false);
        this.heavyPacketThreshold = config.getLong("packet-logger.heavy-packet-threshold", 500000L); // 0.5ms
        this.outputMode = OutputMode.valueOf(config.getString("packet-logger.output", "FILE").toUpperCase());

        this.playerMode = FilterMode.valueOf(config.getString("packet-logger.player-mode", "WHITELIST").toUpperCase());
        this.playerList = new HashSet<>(config.getStringList("packet-logger.players"));

        this.packetMode = FilterMode.valueOf(config.getString("packet-logger.packet-mode", "BLACKLIST").toUpperCase());
        this.packetList = new HashSet<>(config.getStringList("packet-logger.packets"));
    }

    public boolean isEnabled() { return enabled; }
    public long getHeavyPacketThreshold() { return heavyPacketThreshold; }
    public OutputMode getOutputMode() { return outputMode; }
    public Set<String> getPacketList() { return packetList; }
    public Set<String> getPlayerList() { return playerList; }
    public FilterMode getPlayerMode() { return playerMode; }
    public FilterMode getPacketMode() { return packetMode; }

    public enum OutputMode { CONSOLE, FILE, BOTH }
    public enum FilterMode { WHITELIST, BLACKLIST }
}