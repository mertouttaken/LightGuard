package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInSteerVehicle;
import net.minecraft.server.v1_16_R3.PacketPlayInVehicleMove;

public class VehicleCheck extends Check {

    public VehicleCheck(PlayerData data) {
        super(data, "Vehicle");
    }

    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.vehicle.enabled")) return true;

        // 1. Steer Vehicle (Araç Yönlendirme) Exploit
        if (packet instanceof PacketPlayInSteerVehicle) {
            PacketPlayInSteerVehicle steer = (PacketPlayInSteerVehicle) packet;

            // 1.16.5 NMS Metotları:
            // b() -> Strafe (float)
            // c() -> Forward (float)
            float strafe = steer.b();
            float forward = steer.c();

            // NaN (Tanımsız) veya Infinity (Sonsuz) kontrolü
            if (!Float.isFinite(strafe) || !Float.isFinite(forward)) {
                flag("Invalid Steer Values (NaN/Infinity)", "PacketPlayInSteerVehicle");
                return false;
            }

            // d() -> Jump (boolean), e() -> Unmount (boolean)
            boolean jump = steer.d();
            // boolean unmount = steer.e(); // Şu an kullanılmıyor ama erişilebilir.

            // ➤ Elytra Fly / Vehicle Jump Spam Fix
            if (jump) {
                long now = System.currentTimeMillis();

                // Oyuncu çok hızlı art arda zıplama yolluyorsa (Exploit)
                if (now - data.getLastVehicleJump() < 100) {
                    // Kicklemeye gerek yok, sadece paketi iptal et (Ignore)
                    return false;
                }
                data.setLastVehicleJump(now);
            }
        }

        // 2. Vehicle Move (Araç Konumu) Exploit
        if (packet instanceof PacketPlayInVehicleMove) {
            PacketPlayInVehicleMove move = (PacketPlayInVehicleMove) packet;
            String packetName = "PacketPlayInVehicleMove";

            // Koordinat kontrolü
            double x = move.getX();
            double y = move.getY();
            double z = move.getZ();

            // 1. NaN/Infinity Check
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                flag("Invalid Vehicle Coordinates", packetName);
                return false;
            }

            // 2. World Border Check
            if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
                flag("Vehicle Out of World", packetName);
                return false;
            }

            // 3. Entity Check (Araçta değilken araç paketi yollama)
            if (plugin.getConfig().getBoolean("checks.vehicle.prevent-invalid-move")) {
                // Bukkit API thread-safe değildir, bu yüzden dikkatli olunmalı.
                // Ancak getVehicle() ana thread dışında bazen null dönebilir.
                // Kesin emin değilsek sadece cancel (return false) yapıyoruz, flag atmıyoruz.
                if (data.getPlayer().getVehicle() == null) {
                    return false;
                }
            }
        }

        return true;
    }
}