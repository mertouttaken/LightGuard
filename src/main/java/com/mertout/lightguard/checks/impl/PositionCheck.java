package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInFlying;

public class PositionCheck extends Check {

    // Hız hesaplamak için önceki konumu hafızada tutmalıyız
    private double lastX = -1;
    private double lastY = -1;
    private double lastZ = -1;

    public PositionCheck(PlayerData data) {
        super(data, "Position");
    }

    @Override
    public boolean check(Object packet) {

        // ➤ TELEPORT FIX: Oyuncu ışınlandıysa (Admin TP, Spawn vb.)
        // PositionCheck'i 1-2 saniye devre dışı bırakıyoruz ki "Hızlı hareket ettin" diye geri atmasın.
        if (data.isTeleporting()) {
            // Eğer paket konum içeriyorsa, son konumu güncelle ki sonraki hesaplamalar doğru olsun.
            if (packet instanceof PacketPlayInFlying && ((PacketPlayInFlying) packet).hasPos) {
                PacketPlayInFlying p = (PacketPlayInFlying) packet;
                updateLastPos(p.a(0), p.b(0), p.c(0));
            }
            return true; // Kontrol etme, geçmesine izin ver
        }

        if (!plugin.getConfig().getBoolean("checks.position.enabled")) return true;

        if (packet instanceof PacketPlayInFlying) {
            PacketPlayInFlying p = (PacketPlayInFlying) packet;

            // 1.16.5 NMS: 'hasPos' alanı paketin koordinat içerip içermediğini belirtir.
            if (p.hasPos) {
                double x = p.a(0); // getX
                double y = p.b(0); // getY
                double z = p.c(0); // getZ

                // --- 1. NaN & Infinity Check ---
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    flag("NaN/Infinity Coordinate");
                    return false;
                }

                // --- 2. World Border Check ---
                double worldLimit = plugin.getConfig().getDouble("checks.position.out-of-world-limit", 30000000.0);
                if (Math.abs(x) > worldLimit || Math.abs(z) > worldLimit) {
                    flag("Out of World Limit");
                    return false;
                }

                // --- 3. HIZ VEKTÖRÜ HESAPLAMA ---
                // İlk girişte hesap yapamayız, lastX -1 ise kaydet ve çık.
                if (lastX != -1) {
                    double deltaX = x - lastX;
                    double deltaY = y - lastY;
                    double deltaZ = z - lastZ;

                    // 3D Hız (Vektör Uzunluğu)
                    double speed = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

                    // --- 4. ELYTRA CRASH FIX ---
                    // Sadece Elytra ile uçuyorsa hızını kısıtla (Chunk Load Crash Fix)
                    if (data.getPlayer().isGliding()) {
                        double elytraLimit = plugin.getConfig().getDouble("checks.position.elytra-speed-limit", 4.0);

                        if (speed > elytraLimit) {
                            flag("Elytra Speed Limit (" + String.format("%.2f", speed) + ")");
                            return false; // Paketi iptal et, oyuncu olduğu yerde kalsın
                        }
                    }

                    // --- 5. TELEPORT HACK FIX ---
                    // Yürümeye karışmıyoruz ama bir anda 20 blok öteye ışınlanamaz (Teleport Hack)
                    double maxOffset = plugin.getConfig().getDouble("checks.position.max-offset", 20.0);

                    if (speed > maxOffset) {
                        flag("Moved too fast / Teleport Hack (" + String.format("%.2f", speed) + ")");
                        return false;
                    }
                }

                // Son konumu güncelle (Metodu aşağıda tanımladık)
                updateLastPos(x, y, z);
            }
        }
        return true;
    }

    private void updateLastPos(double x, double y, double z) {
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }
}