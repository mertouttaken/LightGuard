package com.mertout.lightguard.checks.impl;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.PlayerData;
import net.minecraft.server.v1_16_R3.PacketPlayInFlying;

public class PositionCheck extends Check {
    public PositionCheck(PlayerData data) { super(data, "Position"); }
    @Override
    public boolean check(Object packet) {
        if (!plugin.getConfig().getBoolean("checks.position.enabled")) return true;
        if (packet instanceof PacketPlayInFlying) {
            PacketPlayInFlying p = (PacketPlayInFlying) packet;
            if (p.a(0) != 0 || p.b(0) != 0) {
                double x = p.a(0), y = p.b(0), z = p.c(0);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) { flag("NaN/Infinity"); return false; }
                if (Math.abs(x) > plugin.getConfig().getDouble("checks.position.max-offset", 300000)) { flag("Limit"); return false; }
            }
        }
        return true;
    }
}