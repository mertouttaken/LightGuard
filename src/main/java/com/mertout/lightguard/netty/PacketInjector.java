package com.mertout.lightguard.netty;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.impl.PayloadCheck;
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

        // Payload kanal verilerini temizle
        // (PayloadCheck.onQuit statik yapıldıysa böyle, değilse plugin.getPayloadCheck().onQuit(...) )
        PayloadCheck.onQuit(event.getPlayer());

        plugin.getPlayerDataManager().removeData(event.getPlayer().getUniqueId());
    }

    private void inject(Player player) {
        // 1. DECODER SONRASI HANDLER (Mevcut Mantık)
        ChannelDuplexHandler channelHandler = new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                Player p = player;

                // SENTINEL MODE: Try-Catch All
                try {
                    long start = System.nanoTime();

                    // Null Check
                    if (plugin.getPacketLoggerManager() != null && plugin.getPacketLoggerManager().getWatchdog() != null) {
                        plugin.getPacketLoggerManager().getWatchdog().startProcessing();
                    }

                    PlayerData data = plugin.getPlayerDataManager().getData(p.getUniqueId());
                    if (data != null) {
                        // CheckManager kontrolü (Flood, NBT vb.)
                        if (!data.getCheckManager().handlePacket(msg)) {
                            // Check başarısız, paketi iptal et
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
                    // FAIL-CLOSED GÜNCELLEMESİ (Güvenli Mod)
                    if (plugin.getConfig().getBoolean("settings.sentinel.enabled", true)) {

                        plugin.getLogger().severe("[Sentinel] Critical Error for " + p.getName() + ": " + t.getMessage());
                        t.printStackTrace();

                        if (!plugin.getConfig().getBoolean("settings.sentinel.silent-failures", true)) {
                            plugin.getServer().getScheduler().runTask(plugin, () ->
                                    p.kickPlayer("§cSecurity Error (LightGuard Sentinel)")
                            );
                        }

                        // Hata varsa paketi düşür (Drop)
                        return;
                    } else {
                        throw t;
                    }
                }
            }
        };

        try {
            ChannelPipeline pipeline = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel.pipeline();

            // 2. RAW INSPECTOR'I EKLE (Decoder Öncesi)
            String targetHandler = "splitter";

            if (pipeline.get("splitter") == null) {
                // HATA DÜZELTİLDİ: pipeline.first() yerine pipeline.firstContext()
                if (pipeline.firstContext() != null) {
                    targetHandler = pipeline.firstContext().name();
                }
            }

            // Raw Inspector zaten yoksa ve hedef handler varsa ekle
            if (pipeline.get("lightguard_raw") == null && pipeline.get(targetHandler) != null) {
                RawPacketInspector inspector = new RawPacketInspector(plugin, player.getName());
                // Splitter'dan (veya ilk handler'dan) hemen sonrasına ekle
                pipeline.addAfter(targetHandler, "lightguard_raw", inspector);
            }

            // 3. MAIN HANDLER (Decoder Sonrası)
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

            // Kanal zaten kapanmışsa veya yoksa işlem yapma
            if (channel == null || !channel.isOpen()) return;

            channel.eventLoop().submit(() -> {
                ChannelPipeline pipeline = channel.pipeline();

                // 1. Main Handler'ı sil
                if (pipeline.get("lightguard_handler") != null) {
                    pipeline.remove("lightguard_handler");
                }

                // 2. YENİ: Raw Inspector'ı sil (Bunu eklemeyi unutma!)
                if (pipeline.get("lightguard_raw") != null) {
                    pipeline.remove("lightguard_raw");
                }
                return null;
            });
        } catch (Exception ignored) {
            // Oyuncu çoktan düşmüş olabilir, hatayı yutuyoruz.
        }
    }

    public void ejectAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            remove(p);
        }
    }
}