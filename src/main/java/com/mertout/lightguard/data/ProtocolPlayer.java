package com.mertout.lightguard.data;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.HybridRateLimiter;
import org.bukkit.entity.Player;
import java.util.UUID;

public class ProtocolPlayer {
    private final Player player;
    private final UUID uuid;
    private final int entityId;

    private final ProtocolStateMachine stateMachine = new ProtocolStateMachine();
    private final HybridRateLimiter rateLimiter;
    private final KeepAliveTracker keepAliveTracker = new KeepAliveTracker();

    private volatile boolean inventoryOpen = false;
    private volatile int openWindowId = 0;
    private volatile boolean isAnvilOpen = false;
    private volatile long lastTeleportTime = 0;

    private volatile boolean isAdmin = false;
    private volatile boolean isCreativeOrSpectator = false;
    private volatile boolean allowedFlight = false;
    private volatile boolean isGliding = false;
    private volatile boolean isDead = false;
    private volatile boolean isHoldingBook = false;
    private volatile boolean brandSent = false;
    private volatile boolean isBedrock = false;
    private volatile boolean isPrinterMode = false;

    private volatile double lastX = -1, lastY = -1, lastZ = -1;
    private volatile double lastVehicleX = -1, lastVehicleY = -1, lastVehicleZ = -1;

    private int pps = 0;
    private int packetCounter = 0;

    private long lastMapTime = 0;
    private int mapCount = 0;

    private long lastBlockPlaceTime = 0;
    private int blockPlaceCount = 0;

    private long lastPrinterTime = 0;
    private int printerCount = 0;

    private long lastRespawnTime = 0;
    private int respawnCount = 0;

    private long lastTabTime = 0;
    private int tabCount = 0;

    private long lastChannelRegisterTime = 0;
    private int recentChannelRegisters = 0;

    private long lastRecipeTime = 0;
    private int recipeCount = 0;

    private long lastQuickMoveTime = 0;
    private int quickMoveCount = 0;

    private long lastEntityInteractTime = 0;

    public ProtocolPlayer(Player player, LightGuard plugin) {
        this.player = player;
        this.uuid = player.getUniqueId();
        this.entityId = player.getEntityId();
        this.rateLimiter = new HybridRateLimiter(plugin);
    }

    public synchronized void incrementPacketCount() { packetCounter++; }
    public synchronized void updatePPS() {
        this.pps = packetCounter;
        this.packetCounter = 0;
    }
    public synchronized int getPPS() { return pps; }

    public synchronized int incrementAndGetMapCount(long now) {
        if (now - lastMapTime > 1000) {
            lastMapTime = now;
            mapCount = 1;
            return 1;
        }
        return ++mapCount;
    }

    public synchronized int incrementAndGetBlockPlaceCount(long now) {
        if (now - lastBlockPlaceTime > 1000) {
            lastBlockPlaceTime = now;
            blockPlaceCount = 1;
            return 1;
        }
        return ++blockPlaceCount;
    }

    public synchronized boolean checkAndSetEntityInteractDelay(long now, long ms) {
        if (now - lastEntityInteractTime < ms) return true;
        lastEntityInteractTime = now;
        return false;
    }

    public synchronized int incrementAndGetPrinterCount(long now) {
        if (now - lastPrinterTime > 1000) {
            lastPrinterTime = now;
            printerCount = 1;
            return 1;
        }
        return ++printerCount;
    }

    public synchronized int incrementAndGetRespawnCount(long now) {
        if (now - lastRespawnTime > 1000) {
            lastRespawnTime = now;
            respawnCount = 1;
            return 1;
        }
        return ++respawnCount;
    }

    public synchronized int incrementAndGetTabCount(long now) {
        if (now - lastTabTime > 1000) {
            lastTabTime = now;
            tabCount = 1;
            return 1;
        }
        return ++tabCount;
    }

    public synchronized int incrementAndGetChannelRegisterCount(int amount, long now) {
        if (now - lastChannelRegisterTime > 60000) {
            lastChannelRegisterTime = now;
            recentChannelRegisters = amount;
            return amount;
        }
        recentChannelRegisters += amount;
        return recentChannelRegisters;
    }

    public synchronized int incrementAndGetRecipeCount(long now) {
        if (now - lastRecipeTime > 1000) {
            lastRecipeTime = now;
            recipeCount = 1;
            return 1;
        }
        return ++recipeCount;
    }

    public synchronized int incrementAndGetQuickMoveCount(long now) {
        if (now - lastQuickMoveTime > 1000) {
            lastQuickMoveTime = now;
            quickMoveCount = 1;
            return 1;
        }
        return ++quickMoveCount;
    }

    public Player getPlayer() { return player; }
    public UUID getUuid() { return uuid; }
    public int getEntityId() { return entityId; }
    public ProtocolStateMachine getStateMachine() { return stateMachine; }
    public HybridRateLimiter getRateLimiter() { return rateLimiter; }
    public KeepAliveTracker getKeepAliveTracker() { return keepAliveTracker; }

    public boolean isInventoryOpen() { return inventoryOpen; }
    public int getOpenWindowId() { return openWindowId; }
    public void setInventoryState(boolean isOpen, int windowId) {
        this.inventoryOpen = isOpen;
        this.openWindowId = isOpen ? windowId : 0;
    }

    public boolean isAnvilOpen() { return isAnvilOpen; }
    public void setAnvilOpen(boolean anvilOpen) { isAnvilOpen = anvilOpen; }
    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }
    public boolean isCreativeOrSpectator() { return isCreativeOrSpectator; }
    public void setCreativeOrSpectator(boolean val) { isCreativeOrSpectator = val; }
    public boolean isAllowedFlight() { return allowedFlight; }
    public void setAllowedFlight(boolean allowedFlight) { this.allowedFlight = allowedFlight; }
    public boolean isGliding() { return isGliding; }
    public void setGliding(boolean gliding) { this.isGliding = gliding; }
    public boolean isDead() { return isDead; }
    public void setDead(boolean dead) { isDead = dead; }
    public boolean isHoldingBook() { return isHoldingBook; }
    public void setHoldingBook(boolean holdingBook) { isHoldingBook = holdingBook; }
    public boolean isBrandSent() { return brandSent; }
    public void setBrandSent(boolean brandSent) { this.brandSent = brandSent; }
    public boolean isBedrock() { return isBedrock; }
    public void setBedrock(boolean bedrock) { isBedrock = bedrock; }
    public void setPrinterMode(boolean mode) { this.isPrinterMode = mode; }
    public boolean isPrinterMode() { return isPrinterMode; }

    public double getLastX() { return lastX; }
    public double getLastY() { return lastY; }
    public double getLastZ() { return lastZ; }
    public void setLastLocation(double x, double y, double z) {
        this.lastX = x; this.lastY = y; this.lastZ = z;
    }
    public double getLastVehicleX() { return lastVehicleX; }
    public double getLastVehicleY() { return lastVehicleY; }
    public double getLastVehicleZ() { return lastVehicleZ; }
    public void setLastVehicleLocation(double x, double y, double z) {
        this.lastVehicleX = x; this.lastVehicleY = y; this.lastVehicleZ = z;
    }
    public boolean isInTeleportGrace() { return (System.currentTimeMillis() - lastTeleportTime) < 1500; }
    public void markTeleport() { this.lastTeleportTime = System.currentTimeMillis(); }
}