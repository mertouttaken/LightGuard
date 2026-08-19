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
    private Set<Class<?>> packetList = new HashSet<>();
    private FilterMode playerMode;
    private FilterMode packetMode;

    private static final String[] PACKET_PACKAGES = {
            "net.minecraft.network.protocol.game.",
            "net.minecraft.network.protocol.common.",
            "net.minecraft.network.protocol.handshake.",
            "net.minecraft.network.protocol.login.",
            "net.minecraft.network.protocol.status."
    };

    public PacketLoggerConfig(LightGuard plugin) {
        reload(plugin);
    }

    public void reload(LightGuard plugin) {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("packet-logger.enabled", false);
        this.heavyPacketThreshold = config.getLong("packet-logger.heavy-packet-threshold", 500000L);
        this.outputMode = parse(plugin, OutputMode.class, config.getString("packet-logger.output"),
                OutputMode.FILE, "packet-logger.output");

        this.playerMode = parse(plugin, FilterMode.class, config.getString("packet-logger.player-mode"),
                FilterMode.WHITELIST, "packet-logger.player-mode");
        this.playerList = new HashSet<>(config.getStringList("packet-logger.players"));

        this.packetMode = parse(plugin, FilterMode.class, config.getString("packet-logger.packet-mode"),
                FilterMode.BLACKLIST, "packet-logger.packet-mode");

        Set<Class<?>> tempPacketList = new HashSet<>();
        for (String packetName : config.getStringList("packet-logger.packets")) {
            for (String pkg : PACKET_PACKAGES) {
                try {
                    tempPacketList.add(Class.forName(pkg + packetName));
                    break;
                } catch (ClassNotFoundException ignored) {}
            }
        }
        this.packetList = tempPacketList;
    }

    /** Falls back to {@code fallback} on an unrecognised value: a config typo must not abort onEnable. */
    private static <E extends Enum<E>> E parse(LightGuard plugin, Class<E> type, String raw, E fallback, String key) {
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid value '" + raw + "' for " + key + "; using " + fallback + ".");
            return fallback;
        }
    }

    public boolean isEnabled() { return enabled; }
    public long getHeavyPacketThreshold() { return heavyPacketThreshold; }
    public OutputMode getOutputMode() { return outputMode; }
    public Set<Class<?>> getPacketList() { return packetList; }
    public Set<String> getPlayerList() { return playerList; }
    public FilterMode getPlayerMode() { return playerMode; }
    public FilterMode getPacketMode() { return packetMode; }

    public enum OutputMode { CONSOLE, FILE, BOTH }
    public enum FilterMode { WHITELIST, BLACKLIST }
}