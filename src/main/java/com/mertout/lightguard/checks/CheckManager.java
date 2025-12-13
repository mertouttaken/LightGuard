package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.PlayerData;
import com.mertout.lightguard.checks.impl.*;
import com.mertout.lightguard.utils.GeyserUtil;
import org.bukkit.Bukkit; // Scheduler için gerekli

import java.util.ArrayList;
import java.util.List;

public class CheckManager {

    private final LightGuard plugin;
    private final PlayerData data;
    private final List<Check> checks = new ArrayList<>();
    private final boolean isBedrock; // Oyuncu Bedrock mu?

    public CheckManager(PlayerData data) {
        this.data = data;
        this.plugin = LightGuard.getInstance();

        // Oyuncunun Bedrock olup olmadığını girişte tespit et
        this.isBedrock = GeyserUtil.isBedrockPlayer(data.getPlayer());

        loadChecks();
    }

    private void loadChecks() {
        // Checkleri yükle
        checks.add(new FloodCheck(data));
        checks.add(new BlockPlaceCheck(data));
        checks.add(new CommandCheck(data));
        checks.add(new TabCheck(data));
        checks.add(new ItemExploitCheck(data));
        checks.add(new PositionCheck(data));
        checks.add(new RecipeCheck(data));
        checks.add(new PayloadCheck(data));
        checks.add(new WindowCheck(data));
        checks.add(new PacketSizeCheck(data));
        checks.add(new SignCheck(data));
        checks.add(new GameStateCheck(data));
        checks.add(new VehicleCheck(data));
        checks.add(new BadPacketCheck(data));
        checks.add(new ResourcePackCheck(data));
        checks.add(new EntityCheck(data));
        checks.add(new BookExploitCheck(data));
        checks.add(new MapExploitCheck(data));
        checks.add(new ChatSecurityCheck(data));
    }

    public boolean handlePacket(Object packet) {
        // Config ayarı: Geyser Support açık mı?
        boolean geyserSupport = plugin.getConfig().getBoolean("settings.sentinel.geyser-support", true);

        // ➤ SENTINEL AYARLARI (YENİ)
        boolean sentinelEnabled = plugin.getConfig().getBoolean("settings.sentinel.enabled", true);
        List<String> criticalChecks = plugin.getConfig().getStringList("settings.sentinel.critical-checks");

        // 1. Offline/Dead Packet Fix
        if (plugin.getConfig().getBoolean("mechanics.block-dead-packets")) {
            if (data.getPlayer().isDead() || !data.getPlayer().isOnline()) {
                String name = packet.getClass().getSimpleName();
                if (name.contains("Window") || name.contains("UseItem") || name.contains("Interact")) {
                    return false;
                }
            }
        }

        // 2. Check Döngüsü
        for (Check check : checks) {

            // ➤ GEYSER BYPASS MANTIĞI:
            if (isBedrock && geyserSupport) {
                String checkClassName = check.getClass().getSimpleName();

                // Bedrock oyuncularında Vehicle ve Position paketleri çok farklıdır, False Positive verir.
                if (checkClassName.equals("VehicleCheck") || checkClassName.equals("PositionCheck")) {
                    continue; // Bu check'i atla (Bypass)
                }
            }

            // ➤ SENTINEL KORUMALI CHECK ÇALIŞTIRMA (Try-Catch Eklendi)
            try {
                if (!check.check(packet)) {
                    return false; // Check başarısız (Hile/Exploit tespit edildi)
                }
            } catch (Throwable t) {
                // --- HATA YÖNETİMİ (SENTINEL) ---
                String checkName = check.getName(); // Check içindeki "super(data, "Isim")" ismidir.

                // 1. Hatayı Logla
                plugin.getLogger().warning("[Sentinel] Error in check '" + checkName + "' for player " + data.getPlayer().getName());

                // Debug modundaysa tam hatayı göster
                if (plugin.getConfig().getBoolean("settings.debug", false)) {
                    t.printStackTrace();
                }

                if (sentinelEnabled) {
                    // 2. Kritiklik Kontrolü (Fail-Closed)
                    // Eğer bu check "Kritik" listesindeyse, hata durumunda paketi ENGELLE.
                    if (criticalChecks.contains(checkName)) {

                        // Opsiyonel: Oyuncuyu at (Sessiz mod kapalıysa)
                        if (!plugin.getConfig().getBoolean("settings.sentinel.silent-failures", true)) {
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    data.getPlayer().kickPlayer("§cSecurity Error: " + checkName + " verification failed.")
                            );
                        }

                        return false;
                    }
                }
            }
        }
        return true;
    }
}