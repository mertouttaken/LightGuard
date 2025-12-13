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

        // Reload sonrası online oyuncuları inject et
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
        PayloadCheck.onQuit(event.getPlayer());

        plugin.getPlayerDataManager().removeData(event.getPlayer().getUniqueId());
    }

    private void inject(Player player) {
        // --- ANA PAKET HANDLER (Packet Object Level) ---
        ChannelDuplexHandler channelHandler = new ChannelDuplexHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                Player p = player;

                // ➤ SENTINEL MODE: Try-Catch All (Sunucu Çökmesini Önler)
                try {
                    long start = System.nanoTime();

                    // 1. Netty Watchdog Başlat (Thread takılırsa uyarır)
                    if (plugin.getPacketLoggerManager() != null && plugin.getPacketLoggerManager().getWatchdog() != null) {
                        plugin.getPacketLoggerManager().getWatchdog().startProcessing();
                    }

                    // 2. CheckManager Kontrolü
                    PlayerData data = plugin.getPlayerDataManager().getData(p.getUniqueId());
                    if (data != null) {
                        // Eğer check başarısız olursa (return false), paketi iptal et.
                        if (!data.getCheckManager().handlePacket(msg)) {
                            return;
                        }
                    }

                    // 3. Paketi Sunucuya İlet
                    super.channelRead(ctx, msg);

                    // 4. Packet Logger İşlemleri (İstatistik & Loglama)
                    long duration = System.nanoTime() - start;

                    if (plugin.getPacketLoggerManager() != null) {
                        if (plugin.getPacketLoggerManager().getWatchdog() != null) {
                            plugin.getPacketLoggerManager().getWatchdog().endProcessing();
                        }
                        // Sadece debug veya log açıkken çalışır, performans yemez.
                        plugin.getPacketLoggerManager().processPacket(p, msg, duration);
                    }

                } catch (Throwable t) {
                    // ➤ FAIL-CLOSED (Güvenli Mod)
                    // Bir hata oluşursa (NPE, ClassCastException vb.) sunucu çökmesin.
                    if (plugin.getConfig().getBoolean("settings.sentinel.enabled", true)) {

                        plugin.getLogger().severe("[Sentinel] Critical Error for " + p.getName() + ": " + t.getMessage());

                        if (plugin.getConfig().getBoolean("settings.debug", false)) {
                            t.printStackTrace();
                        }

                        if (!plugin.getConfig().getBoolean("settings.sentinel.silent-failures", true)) {
                            plugin.getServer().getScheduler().runTask(plugin, () ->
                                    p.kickPlayer("§cSecurity Error (LightGuard Sentinel)")
                            );
                        }

                        // Hata varsa paketi düşür (Drop)
                        return;
                    } else {
                        // Sentinel kapalıysa hatayı fırlat (Sunucu çöker)
                        throw t;
                    }
                }
            }
        };

        // --- PIPELINE ENJEKSİYONU ---
        try {
            ChannelPipeline pipeline = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel.pipeline();

            // ➤ GÜVENLİ HEDEF BELİRLEME (ViaVersion / Paper Uyumluluğu)
            // Sadece "splitter"a güvenmek yerine, olası tüm noktaları deniyoruz.
            String[] targetHandlers = {"splitter", "decoder", "prepender", "packet_handler"};
            String target = null;

            for (String handlerName : targetHandlers) {
                if (pipeline.get(handlerName) != null) {
                    target = handlerName;
                    break;
                }
            }

            // 1. RAW INSPECTOR EKLEME (ByteBuf Seviyesi - Decoder Öncesi)
            // Bu katman, paketi deserialize etmeden önce boyutunu (Size) kontrol eder.
            if (pipeline.get("lightguard_raw") == null) {
                RawPacketInspector inspector = new RawPacketInspector(plugin, player.getName());

                if (pipeline.context("decoder") != null) {
                    // Decoder varsa hemen öncesine koy (En güvenli yer)
                    pipeline.addBefore("decoder", "lightguard_raw", inspector);
                } else if (target != null) {
                    // Decoder yoksa bulduğumuz hedef handler'dan sonraya koy
                    pipeline.addAfter(target, "lightguard_raw", inspector);
                } else {
                    // Hiçbir şey yoksa en başa koy
                    pipeline.addFirst("lightguard_raw", inspector);
                }
            }

            // 2. MAIN HANDLER EKLEME (Packet Object Seviyesi - Decoder Sonrası)
            // Bu katman, paketin içeriğini (NBT, Slot, Koordinat) kontrol eder.
            if (pipeline.get("lightguard_handler") == null) {
                if (pipeline.get("packet_handler") != null) {
                    pipeline.addBefore("packet_handler", "lightguard_handler", channelHandler);
                } else {
                    // Çok nadir durum: packet_handler yoksa sona ekle
                    pipeline.addLast("lightguard_handler", channelHandler);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void remove(Player player) {
        try {
            // Player handle alırken null check
            if (player == null || !player.isOnline()) return;

            Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;

            if (channel != null && channel.isOpen()) {
                // Netty thread'ine işi güvenli şekilde atıyoruz (Race Condition Fix)
                channel.eventLoop().execute(() -> {
                    ChannelPipeline pipeline = channel.pipeline();
                    if (pipeline.get("lightguard_handler") != null) pipeline.remove("lightguard_handler");
                    if (pipeline.get("lightguard_raw") != null) pipeline.remove("lightguard_raw");
                });
            }
        } catch (Exception e) {
            // Oyuncu çıkarken channel zaten kapanmış olabilir, loglamaya gerek yok.
        }
    }

    public void ejectAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            remove(p);
        }
    }
}