package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;

import java.util.List;
import java.util.stream.Collectors;

public abstract class Check {
    protected final LightGuard plugin;
    protected final PlayerData data;
    protected final String name;
    protected final boolean enabled;

    public Check(PlayerData data, String name, String configName) {
        this.plugin = LightGuard.getInstance();
        this.data = data;
        this.name = name;
        this.enabled = plugin.getConfig().getBoolean("checks." + configName + ".enabled");
    }

    public abstract boolean check(Object packet);

    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }

    public boolean isBedrockCompatible() {
        return true;
    }

    protected void flag(String info, String packetName) {
        int ping = getPing();
        double tps = plugin.getTPS();
        Location loc = data.getPlayer().getLocation();
        String locationStr = String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());

        plugin.getLogger().warning(String.format(
                "§c[LightGuard] %s flagged %s (%s) | Info: %s | Ping: %dms | TPS: %.2f | Loc: %s",
                data.getPlayer().getName(), name, packetName, info, ping, tps, locationStr
        ));

        if(plugin.getConfig().getBoolean("settings.sentinel.enabled") &&
                plugin.getConfig().getBoolean("settings.sentinel.silent-failures")) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            String kickMessage = buildKickMessage(packetName, ping);
            data.getPlayer().kickPlayer(kickMessage);
        });
    }

    private int getPing() {
        try { return ((CraftPlayer) data.getPlayer()).getHandle().ping; } catch (Exception e) { return -1; }
    }

    private String buildKickMessage(String packetName, int ping) {
        List<String> layout = plugin.getConfig().getStringList("settings.kick-layout");
        return layout.stream()
                .map(line -> line
                        .replace("%player%", data.getPlayer().getName())
                        .replace("%check%", name)
                        .replace("%packet%", packetName)
                        .replace("%ping%", String.valueOf(ping))
                        .replace("&", "§"))
                .collect(Collectors.joining("\n"));
    }
}