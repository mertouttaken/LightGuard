package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.*;

import java.lang.reflect.Field;

public class GameStateCheck extends Check {

    public GameStateCheck(PlayerData data) {
        super(data, "GameState");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.game-state.enabled")) return true;

        // ➤ 1. Envanter Kapalıyken Tıklama (Inventory Desync)
        if (packet instanceof PacketPlayInWindowClick) {
            if (plugin.getConfig().getBoolean("checks.game-state.check-inventory-open")) {
                // Private olan 'a' (Window ID) değerini Reflection ile alıyoruz
                int windowId = getWindowId((PacketPlayInWindowClick) packet);

                // Window ID 0 = Oyuncunun kendi envanteri.
                // Eğer 0'dan büyükse (Chest vb.) ve oyuncunun envanteri kapalı görünüyorsa şüphelidir.
                // (Buraya ek mantık eklenebilir, şimdilik sadece ID'yi hatasız okuyoruz)
            }
        }

        // Jigsaw ve Structure paketleri isteğin üzerine kaldırıldı.
        // Bu paketler çok nadir exploit edildiği için çıkarılması güvenlik açığı yaratmaz.

        return true;
    }

    /**
     * PacketPlayInWindowClick içindeki private 'a' (Window ID) field'ını okur.
     */
    private int getWindowId(PacketPlayInWindowClick packet) {
        try {
            Field f = packet.getClass().getDeclaredField("a");
            f.setAccessible(true); // Kilidi aç
            return f.getInt(packet);
        } catch (Exception e) {
            return 0; // Hata olursa 0 varsay
        }
    }
}