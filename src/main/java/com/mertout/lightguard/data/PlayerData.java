package com.mertout.lightguard.data;

import com.mertout.lightguard.checks.CheckManager;
import org.bukkit.entity.Player;
import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private final Player player;
    private final CheckManager checkManager;
    private boolean printerMode = false;
    private long lastTeleportTime;

    private int currentPPS = 0;

    private long lastVehicleJump;

    public int getPPS() { return currentPPS; }
    public void setPPS(int pps) { this.currentPPS = pps; }

    public PlayerData(Player player) {
        this.player = player;
        this.uuid = player.getUniqueId();
        this.checkManager = new CheckManager(this);
        this.lastVehicleJump = 0;
    }
    public void setLastTeleportTime(long time) { this.lastTeleportTime = time; }
    public boolean isTeleporting() { return System.currentTimeMillis() - lastTeleportTime < 2000; }
    public long getLastVehicleJump() { return lastVehicleJump; }
    public void setLastVehicleJump(long lastVehicleJump) { this.lastVehicleJump = lastVehicleJump; }
    public Player getPlayer() { return player; }
    public UUID getUuid() { return uuid; }
    public CheckManager getCheckManager() { return checkManager; }
    public boolean isPrinterMode() { return printerMode; }
    public void setPrinterMode(boolean printerMode) { this.printerMode = printerMode; }
}