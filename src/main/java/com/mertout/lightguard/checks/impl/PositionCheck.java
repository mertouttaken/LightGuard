package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInFlying;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class PositionCheck extends Check {

    private static final VarHandle X, Y, Z, HAS_POS;
    private static final VarHandle YAW, PITCH, HAS_LOOK;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            X = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "x", double.class);
            Y = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "y", double.class);
            Z = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "z", double.class);
            HAS_POS = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "hasPos", boolean.class);
            YAW = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "yaw", float.class);
            PITCH = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "pitch", float.class);
            HAS_LOOK = MethodHandles.privateLookupIn(PacketPlayInFlying.class, lookup).findVarHandle(PacketPlayInFlying.class, "hasLook", boolean.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    public PositionCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInFlying)) return true;

        try {
            // --- 1. ROTASYON (YAW/PITCH) KONTROLLERİ ---
            boolean hasLook = (boolean) HAS_LOOK.get(packet);
            if (hasLook) {
                float yaw = (float) YAW.get(packet);
                float pitch = (float) PITCH.get(packet);

                if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Rotation (NaN/Inf)");
                    return false;
                }

                if (Math.abs(pitch) > 90.0F) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Pitch Angle (" + pitch + ")");
                    return false;
                }

                if (Math.abs(yaw) > 3.0E7F) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Extreme Yaw Angle Exploit");
                    return false;
                }
            }

            // --- 2. POZİSYON (X/Y/Z) KONTROLLERİ ---
            boolean hasPos = (boolean) HAS_POS.get(packet);
            if (!hasPos) return true;

            double x = (double) X.get(packet);
            double y = (double) Y.get(packet);
            double z = (double) Z.get(packet);

            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Coordinates (NaN/Inf)");
                return false;
            }

            // SG YAMASI: Integer Overflow Koruması (Devasa sayılarla sunucuyu çökertme)
            if (Math.abs(x) >= 2.147483647E9 || Math.abs(z) >= 2.147483647E9 || Math.abs(y) >= 1.0E9) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Impossible Position (Overflow)");
                return false;
            }

            if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "World Border Exploit");
                return false;
            }

            // --- 3. ASENKRON CHUNK AWARENESS (Chunk Crasher & NoCom Koruması) ---
            int chunkX = (int) x >> 4;
            int chunkZ = (int) z >> 4;
            String worldName = player.getPlayer().getWorld().getName();

            // Saniyenin binde birinde O(1) hızında Lock-Free chunk sorgusu
            if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(worldName, chunkX, chunkZ)) {
                if (!player.isInTeleportGrace()) {
                    // Yüklenmemiş chunk'a gitmeye çalışıyor, paketi sessizce REDDET (laglı gibi geriye atar)
                    return false;
                }
            }

            // --- 4. HAREKET (MOVEMENT) & HIZ ANALİZİ ---
            double lastX = player.getLastX();
            double lastY = player.getLastY();
            double lastZ = player.getLastZ();

            if (lastX != -1) {
                double diffX = Math.abs(x - lastX);
                double diffY = Math.abs(y - lastY);
                double diffZ = Math.abs(z - lastZ);

                if (player.isInTeleportGrace()) {
                    player.setLastLocation(x, y, z);
                    return true;
                }

                // 🛡️ DÜZELTME: Float / Double Sayılar "==" ile karşılaştırılamaz!
                // Epsilon mantığı kullanılarak 0.1 fly tespiti çok daha keskinleştirildi.
                if (Math.abs(diffY - 0.1) < 1e-7 && !player.isInTeleportGrace()) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Vertical Step (0.1 Fly Exploit)");
                    return false;
                }

                if ((diffX >= 10.0 && diffX % 1.0 == 0.0) ||
                        (diffZ >= 10.0 && diffZ % 1.0 == 0.0) ||
                        (diffY >= 10.0 && diffY % 1.0 == 0.0)) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Chunk Crasher (Integer Step)");
                    return false;
                }

                double distSq = (diffX * diffX) + (diffY * diffY) + (diffZ * diffZ);
                if (distSq > 100.0) {
                    plugin.getActionQueue().pushKick(player.getPlayer(), "Excessive Speed / Blink Exploit");
                    return false;
                }
            }

            // Durumu ProtocolPlayer'a (Oyuncuya özel objeye) kaydet
            player.setLastLocation(x, y, z);

        } catch (Exception e) {
            return false; // Hata durumunda fail-safe olarak paketi yut
        }

        return true;
    }
    @Override
    public boolean isBedrockCompatible() {
        return false;
    }
}