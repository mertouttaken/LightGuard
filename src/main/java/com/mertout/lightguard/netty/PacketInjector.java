package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.PlayerData;
import io.netty.channel.*;
import net.minecraft.server.v1_16_R3.MinecraftServer;
import net.minecraft.server.v1_16_R3.PacketPlayOutKeepAlive;
import net.minecraft.server.v1_16_R3.ServerConnection;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.List;
import java.util.NoSuchElementException;

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

        injectServerConnection();

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            plugin.getPlayerDataManager().createData(p);
            injectPlayer(p);
        }
    }

    private void injectServerConnection() {
        try {
            ServerConnection serverConnection = ((CraftServer) Bukkit.getServer()).getServer().getServerConnection();
            if (serverConnection == null) return;

            Field channelsField = ServerConnection.class.getDeclaredField("g");
            channelsField.setAccessible(true);
            List<ChannelFuture> futures = (List<ChannelFuture>) channelsField.get(serverConnection);

            for (ChannelFuture future : futures) {
                future.channel().pipeline().addFirst(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof Channel) {
                            Channel channel = (Channel) msg;
                            channel.pipeline().addFirst("lightguard_raw", new RawPacketInspector(plugin, "PRE_LOGIN_CONNECTION"));
                        }
                        super.channelRead(ctx, msg);
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to inject into ServerConnection (Pre-Login protection might be disabled): " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPlayerDataManager().createData(event.getPlayer());
        injectPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        try {
            removePlayer(event.getPlayer());
        } finally {
            plugin.getPlayerDataManager().removeData(event.getPlayer().getUniqueId());
        }
    }

    private void injectPlayer(Player player) {
        try {
            Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;

            if (channel.pipeline().get("lightguard_handler") != null) {
                channel.pipeline().remove("lightguard_handler");
            }

            ChannelDuplexHandler channelHandler = createPacketHandler(player);

            String target = "packet_handler";
            if (channel.pipeline().get(target) != null) {
                channel.pipeline().addBefore(target, "lightguard_handler", channelHandler);
            } else {
                channel.pipeline().addLast("lightguard_handler", channelHandler);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to inject player " + player.getName() + ": " + e.getMessage());
        }
    }

    private ChannelDuplexHandler createPacketHandler(Player player) {
        return new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                long start = System.nanoTime();
                boolean blocked = false;

                try {
                    PlayerData data = plugin.getPlayerDataManager().getData(player.getUniqueId());
                    if (data != null) {
                        if (!data.getCheckManager().handlePacket(msg)) {
                            blocked = true;
                            return;
                        }
                    }
                    super.channelRead(ctx, msg);
                } catch (Exception e) {
                    if (plugin.getConfig().getBoolean("settings.sentinel.enabled", true)) {
                        plugin.getLogger().warning("[Sentinel] Packet error from " + player.getName() + ": " + e.getMessage());
                        return;
                    }
                    throw e;
                } finally {
                    long duration = System.nanoTime() - start;
                    if (plugin.getMetrics() != null) {
                        plugin.getMetrics().recordPacket(blocked, duration);
                    }
                    if (plugin.getPacketLoggerManager() != null) {
                        plugin.getPacketLoggerManager().processPacket(player, msg, duration);
                    }
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                Throwable root = cause;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                String msg = root.getMessage();
                if (msg != null && msg.contains("Expected root tag to be a CompoundTag") && msg.contains("was 69")) {
                    plugin.getLogger().warning("§c[LightGuard] Protocol Exploit (Bad NBT 69) blocked from " + player.getName());
                    Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer("§cInvalid Protocol Data"));
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
                        } catch (Exception ignored) {}
                    }
                }
                super.write(ctx, msg, promise);
            }
        };
    }

    private void removePlayer(Player player) {
        try {
            if (player == null || !player.isOnline()) return;
            Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
            if (channel != null) {
                if (channel.pipeline().get("lightguard_handler") != null) {
                    channel.pipeline().remove("lightguard_handler");
                }
            }
        } catch (Exception ignored) {}
    }

    public void ejectAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            removePlayer(p);
        }
    }
}