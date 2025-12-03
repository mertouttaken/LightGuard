package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.PlayerData;
import io.netty.channel.*;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PacketInjector implements Listener {

    private final LightGuard plugin;

    public PacketInjector(LightGuard plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            plugin.getPlayerDataManager().createData(p);
            inject(p);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPlayerDataManager().createData(event.getPlayer());
        inject(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer());
        plugin.getPlayerDataManager().removeData(event.getPlayer().getUniqueId());
    }

    private void inject(Player player) {
        ChannelDuplexHandler channelHandler = new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                Player p = player;

                // SENTINEL MODE: Try-Catch All
                try {
                    long start = System.nanoTime();
                    plugin.getPacketLoggerManager().getWatchdog().startProcessing();

                    PlayerData data = plugin.getPlayerDataManager().getData(p.getUniqueId());
                    if (data != null) {
                        if (!data.getCheckManager().handlePacket(msg)) {
                            // Check başarısız, paketi iptal et
                            return;
                        }
                    }

                    super.channelRead(ctx, msg);

                    long duration = System.nanoTime() - start;
                    plugin.getPacketLoggerManager().getWatchdog().endProcessing();
                    plugin.getPacketLoggerManager().processPacket(p, msg, duration);

                } catch (Throwable t) {
                    if (plugin.getConfig().getBoolean("settings.sentinel.enabled", true)) {
                        plugin.getLogger().severe("[Sentinel] Error for " + p.getName() + ": " + t.getMessage());
                        if (!plugin.getConfig().getBoolean("settings.sentinel.silent-failures", true)) {
                            p.kickPlayer("§cSecurity Error (Sentinel)");
                        }
                        super.channelRead(ctx, msg);
                    } else {
                        throw t;
                    }
                }
            }
            // NOT: write metodu (Outbound) tamamen kaldırıldı.
        };

        try {
            ChannelPipeline pipeline = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel.pipeline();
            if (pipeline.get("lightguard_handler") == null) {
                pipeline.addBefore("packet_handler", "lightguard_handler", channelHandler);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void remove(Player player) {
        try {
            Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
            channel.eventLoop().submit(() -> {
                channel.pipeline().remove("lightguard_handler");
                return null;
            });
        } catch (Exception ignored) {}
    }

    public void ejectAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            remove(p);
        }
    }
}