package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.PlayerData;
import com.mertout.lightguard.checks.impl.*;
import com.mertout.lightguard.utils.GeyserUtil; // IMPORT EKLENDİ

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
    }

    public boolean handlePacket(Object packet) {
        // Config ayarı: Geyser Support açık mı?
        boolean geyserSupport = plugin.getConfig().getBoolean("settings.sentinel.geyser-support");

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
            // Eğer oyuncu Bedrock ise ve Geyser Support açıksa;
            // Bazı hassas checkleri (Vehicle, Position, Flood) atlayabiliriz veya esnetebiliriz.
            if (isBedrock && geyserSupport) {
                String checkName = check.getClass().getSimpleName();

                // Bedrock oyuncularında Vehicle ve Position paketleri çok farklıdır, False Positive verir.
                if (checkName.equals("VehicleCheck") || checkName.equals("PositionCheck")) {
                    continue; // Bu check'i atla (Bypass)
                }

                // Not: FloodCheck'i kapatmıyoruz ama Bedrock oyuncuları biraz daha fazla paket atabilir.
                // Eğer Flood'dan atılırlarsa buraya || checkName.equals("FloodCheck") ekleyebilirsin.
            }

            if (!check.check(packet)) {
                return false;
            }
        }
        return true;
    }
}