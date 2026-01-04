package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.PlayerData;
import io.netty.channel.*;
import net.minecraft.server.v1_16_R3.PacketPlayOutKeepAlive;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class PacketInjector implements Listener {

    private final LightGuard plugin;
    private static final VarHandle KEEP_ALIVE_ID;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayOutKeepAlive.class, MethodHandles.lookup());
            KEEP_ALIVE_ID = lookup.findVarHandle(PacketPlayOutKeepAlive.class, "a", long.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
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
        try {
            removeSync(event.getPlayer());
        } finally {
            plugin.getPlayerDataManager().removeData(event.getPlayer().getUniqueId());
        }
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
                        if (!data.getCheckManager().handlePacket(msg)) return;
                    }
                    super.channelRead(ctx, msg);
                    long duration = System.nanoTime() - start;
                    if (plugin.getPacketLoggerManager() != null) {
                        plugin.getPacketLoggerManager().getWatchdog().endProcessing();
                        plugin.getPacketLoggerManager().processPacket(p, msg, duration);
                    }
                } catch (Exception e) {
                    if (plugin.getConfig().getBoolean("settings.sentinel.enabled", true)) {
                        plugin.getLogger().warning("[Sentinel] Packet error from " + p.getName() + ": " + e.getMessage());
                        return;
                    }
                    throw e;
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                Player p = player;
                Throwable rootCause = cause;
                while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                    rootCause = rootCause.getCause();
                }

                String message = rootCause.getMessage();
                if (message != null && message.contains("Expected root tag to be a CompoundTag") && message.contains("was 69")) {
                    plugin.getLogger().warning("§c[LightGuard] Protocol Exploit (Bad NBT Tag 69) detected and blocked from " + p.getName() + ".");
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            p.kickPlayer("§4[LightGuard] &cBad Package (NBT Exploit) Detected. Please reconnect."));
                    return;
                }

                super.exceptionCaught(ctx, cause);
            }

            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof PacketPlayOutKeepAlive) {
                    PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
                    if (data != null) {
                        try {
                            long id = (long) KEEP_ALIVE_ID.get(msg);
                            data.getPendingKeepAlives().put(id, System.currentTimeMillis());
                        } catch (Exception e) {}
                    }
                }
                super.write(ctx, msg, promise);
            }
        };

        try {
            ChannelPipeline pipeline = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel.pipeline();

            try {
                if (pipeline.get("lightguard_handler") != null) pipeline.remove("lightguard_handler");
                if (pipeline.get("lightguard_raw") != null) pipeline.remove("lightguard_raw");
            } catch (Exception ignored) {
            }

            String[] targetHandlers = {"splitter", "decoder", "prepender", "packet_handler"};
            String target = null;
            for (String handlerName : targetHandlers) {
                if (pipeline.get(handlerName) != null) { target = handlerName; break; }
            }

            RawPacketInspector inspector = new RawPacketInspector(plugin, player.getName());
            try {
                if (pipeline.context("decoder") != null) pipeline.addBefore("decoder", "lightguard_raw", inspector);
                else if (target != null) pipeline.addAfter(target, "lightguard_raw", inspector);
                else pipeline.addFirst("lightguard_raw", inspector);
            } catch (IllegalArgumentException e) {
            }

            try {
                if (pipeline.get("packet_handler") != null) pipeline.addBefore("packet_handler", "lightguard_handler", channelHandler);
                else pipeline.addLast("lightguard_handler", channelHandler);
            } catch (IllegalArgumentException e) {
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to inject " + player.getName() + ": " + e.getMessage());
        }
    }

    private void removeSync(Player player) {
        try {
            if (player == null || !player.isOnline()) return;

            Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;

            if (channel != null) {
                ChannelPipeline pipeline = channel.pipeline();

                try {
                    if (pipeline.get("lightguard_handler") != null) pipeline.remove("lightguard_handler");
                } catch (Exception ignored) {}

                try {
                    if (pipeline.get("lightguard_raw") != null) pipeline.remove("lightguard_raw");
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
        }
    }

    public void remove(Player player) {
        removeSync(player);
    }

    public void ejectAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) { remove(p); }
    }
}