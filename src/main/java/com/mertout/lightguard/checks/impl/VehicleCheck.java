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

        // 1. Steer Vehicle (Yönlendirme) Exploit
        if (packet instanceof PacketPlayInSteerVehicle) {
            PacketPlayInSteerVehicle steer = (PacketPlayInSteerVehicle) packet;

            // 1.16.5'te 'a' = Strafe (Yan), 'b' = Forward (İleri)
            // Bu değerler NaN (Tanımsız) veya Infinity (Sonsuz) olursa sunucu fiziği çöker.
            float strafe = steer.b(); // method isimleri b() ve c() olabilir, field erişimi daha güvenli:
            float forward = steer.c();

            if (!Float.isFinite(strafe) || !Float.isFinite(forward)) {
                flag("Invalid Steer Values (NaN/Infinity)");
                return false;
            }
        }

        // 2. Vehicle Move (Hareket) Exploit
        if (packet instanceof PacketPlayInVehicleMove) {
            PacketPlayInVehicleMove move = (PacketPlayInVehicleMove) packet;

            // Koordinat kontrolü
            double x = move.getX();
            double y = move.getY();
            double z = move.getZ();

            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                flag("Invalid Vehicle Coordinates");
                return false;
            }

            if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
                flag("Vehicle Out of World");
                return false;
            }

            // Oyuncu bir araçta değilse ama araç paketi yolluyorsa?
            if (data.getPlayer().getVehicle() == null && plugin.getConfig().getBoolean("checks.vehicle.prevent-invalid-move")) {
                // Lag yüzünden false-positive olabilir, kick yerine cancel tercih edilir.
                return false;
            }
        }

        return true;
    }
}