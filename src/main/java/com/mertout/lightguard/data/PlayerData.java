package com.mertout.lightguard.data;

import com.mertout.lightguard.checks.CheckManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerData {

    private final UUID uuid;
    private final Player player;
    private final CheckManager checkManager;

    private volatile boolean printerMode = false;
    private volatile long lastTeleportTime;
    private volatile int currentPPS = 0;
    private long lastVehicleJump;

    private volatile GameMode gameMode;

    private volatile int teleportBurst = 0;

    private final AtomicLong lastChannelRegister = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger recentChannelRegisters = new AtomicInteger(0);

    private final Map<Long, Long> pendingKeepAlives = new ConcurrentHashMap<>();
    private final Set<String> registeredChannels = ConcurrentHashMap.newKeySet();

    public PlayerData(Player player) {
        this.player = player;
        this.uuid = player.getUniqueId();
        this.checkManager = new CheckManager(this);
        this.lastVehicleJump = 0;
        this.lastTeleportTime = System.currentTimeMillis();
        this.gameMode = player.getGameMode();
    }

    public void clearSecurityData() {
        pendingKeepAlives.clear();
        registeredChannels.clear();
    }

    public void cleanOldKeepAlives() {
        long now = System.currentTimeMillis();
        pendingKeepAlives.entrySet().removeIf(entry -> (now - entry.getValue()) > 60000);
    }

    public AtomicLong getLastChannelRegister() { return lastChannelRegister; }
    public AtomicInteger getRecentChannelRegisters() { return recentChannelRegisters; }

    public Set<String> getRegisteredChannels() { return registeredChannels; }
    public Map<Long, Long> getPendingKeepAlives() { return pendingKeepAlives; }

    public int getPPS() { return currentPPS; }
    public void setPPS(int pps) { this.currentPPS = pps; }

    public void setLastTeleportTime(long time) {
        if (time - this.lastTeleportTime < 600L) {
            teleportBurst++;
        } else {
            teleportBurst = 0;
        }
        this.lastTeleportTime = time;
    }

    public boolean isTeleporting() {
        if (teleportBurst > 3) {
            return false;
        }
        return System.currentTimeMillis() - lastTeleportTime < 750L;
    }

    public long getLastVehicleJump() { return lastVehicleJump; }
    public void setLastVehicleJump(long lastVehicleJump) { this.lastVehicleJump = lastVehicleJump; }

    public Player getPlayer() { return player; }
    public UUID getUuid() { return uuid; }
    public CheckManager getCheckManager() { return checkManager; }

    public boolean isPrinterMode() { return printerMode; }
    public void setPrinterMode(boolean printerMode) { this.printerMode = printerMode; }

    public GameMode getGameMode() { return gameMode; }
    public void setGameMode(GameMode gameMode) { this.gameMode = gameMode; }
}