package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.impl.PayloadCheck;
import com.mertout.lightguard.data.PlayerData;
import io.netty.channel.*;
import net.minecraft.server.v1_16_R3.PacketPlayOutKeepAlive;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Field;

public class PacketInjector implements Listener {

    private final LightGuard plugin;
    private static Field keepAliveIdField;

    static {
        try {
            keepAliveIdField = PacketPlayOutKeepAlive.class.getDeclaredField("a");
            keepAliveIdField.setAccessible(true);
        } catch (Exception e) { e.printStackTrace(); }
    }

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
                try {
                    long start = System.nanoTime();
                    if (plugin.getPacketLoggerManager() != null && plugin.getPacketLoggerManager().getWatchdog() != null) {
                        plugin.getPacketLoggerManager().getWatchdog().startProcessing();
                    }

                    PlayerData data = plugin.getPlayerDataManager().getData(p.getUniqueId());
                    if (data != null) {
                        if (!data.getCheckManager().handlePacket(msg)) {
                            return;
                        }
                    }

                    super.channelRead(ctx, msg);

                    long duration = System.nanoTime() - start;
                    if (plugin.getPacketLoggerManager() != null) {
                        if (plugin.getPacketLoggerManager().getWatchdog() != null) {
                            plugin.getPacketLoggerManager().getWatchdog().endProcessing();
                        }
                        plugin.getPacketLoggerManager().processPacket(p, msg, duration);
                    }
                } catch (Throwable t) {
                    if (plugin.getConfig().getBoolean("settings.sentinel.enabled", true)) {
                        plugin.getLogger().severe("[Sentinel] Critical Error for " + p.getName() + ": " + t.getMessage());
                        if (plugin.getConfig().getBoolean("settings.debug", false)) t.printStackTrace();

                        if (!plugin.getConfig().getBoolean("settings.sentinel.silent-failures", true)) {
                            plugin.getServer().getScheduler().runTask(plugin, () ->
                                    p.kickPlayer("§cSecurity Error (LightGuard Sentinel)")
                            );
                        }
                        return;
                    } else {
                        throw t;
                    }
                }
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof PacketPlayOutKeepAlive) {
                    PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
                    if (data != null) {
                        try {
                            long id = keepAliveIdField.getLong(msg);
                            data.getPendingKeepAlives().add(id);
                        } catch (Exception e) {}
                    }
                }
                super.write(ctx, msg, promise);
            }
        };

        try {
            ChannelPipeline pipeline = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel.pipeline();
            String[] targetHandlers = {"splitter", "decoder", "prepender", "packet_handler"};
            String target = null;
            for (String handlerName : targetHandlers) {
                if (pipeline.get(handlerName) != null) { target = handlerName; break; }
            }

            if (pipeline.get("lightguard_raw") == null) {
                RawPacketInspector inspector = new RawPacketInspector(plugin, player.getName());
                if (pipeline.context("decoder") != null) pipeline.addBefore("decoder", "lightguard_raw", inspector);
                else if (target != null) pipeline.addAfter(target, "lightguard_raw", inspector);
                else pipeline.addFirst("lightguard_raw", inspector);
            }

            if (pipeline.get("lightguard_handler") == null) {
                if (pipeline.get("packet_handler") != null) pipeline.addBefore("packet_handler", "lightguard_handler", channelHandler);
                else pipeline.addLast("lightguard_handler", channelHandler);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void remove(Player player) {
        try {
            if (player == null || !player.isOnline()) return;
            Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
            if (channel != null && channel.isOpen()) {
                channel.eventLoop().execute(() -> {
                    ChannelPipeline pipeline = channel.pipeline();
                    if (pipeline.get("lightguard_handler") != null) pipeline.remove("lightguard_handler");
                    if (pipeline.get("lightguard_raw") != null) pipeline.remove("lightguard_raw");
                });
            }
        } catch (Exception e) {}
    }

    public void ejectAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) { remove(p); }
    }
}