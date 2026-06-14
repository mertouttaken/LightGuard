package com.mertout.lightguard.data;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.HybridRateLimiter;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ProtocolPlayer {
    private final Player player;
    private final UUID uuid;
    private final int entityId;

    private final ProtocolStateMachine stateMachine = new ProtocolStateMachine();
    private final HybridRateLimiter rateLimiter;
    private final KeepAliveTracker keepAliveTracker = new KeepAliveTracker();

    private static final class InventoryState {
        final boolean isOpen;
        final int windowId;
        InventoryState(boolean isOpen, int windowId) {
            this.isOpen = isOpen;
            this.windowId = windowId;
        }
    }
    private static final InventoryState CLOSED_STATE = new InventoryState(false, 0);
    private volatile InventoryState inventoryState = CLOSED_STATE;

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

    private final AtomicInteger pps = new AtomicInteger(0);
    private final AtomicInteger packetCounter = new AtomicInteger(0);

    private final AtomicLong lastMapTime = new AtomicLong(0);
    private final AtomicInteger mapCount = new AtomicInteger(0);

    private final AtomicLong lastBlockPlaceTime = new AtomicLong(0);
    private final AtomicInteger blockPlaceCount = new AtomicInteger(0);

    private final AtomicLong lastPrinterTime = new AtomicLong(0);
    private final AtomicInteger printerCount = new AtomicInteger(0);

    private final AtomicLong lastRespawnTime = new AtomicLong(0);
    private final AtomicInteger respawnCount = new AtomicInteger(0);

    private final AtomicLong lastTabTime = new AtomicLong(0);
    private final AtomicInteger tabCount = new AtomicInteger(0);

    private final AtomicLong lastChannelRegisterTime = new AtomicLong(0);
    private final AtomicInteger recentChannelRegisters = new AtomicInteger(0);

    private final AtomicLong lastRecipeTime = new AtomicLong(0);
    private final AtomicInteger recipeCount = new AtomicInteger(0);

    private final AtomicLong lastQuickMoveTime = new AtomicLong(0);
    private final AtomicInteger quickMoveCount = new AtomicInteger(0);

    private final AtomicLong lastEntityInteractTime = new AtomicLong(0);

    public ProtocolPlayer(Player player, LightGuard plugin) {
        this.player = player;
        this.uuid = player.getUniqueId();
        this.entityId = player.getEntityId();
        this.rateLimiter = new HybridRateLimiter(plugin);
    }


    public void incrementPacketCount() { packetCounter.incrementAndGet(); }
    public void updatePPS() { pps.set(packetCounter.getAndSet(0)); }
    public int getPPS() { return pps.get(); }

    public int incrementAndGetMapCount(long now) {
        if (now - lastMapTime.get() > 1000) {
            lastMapTime.set(now);
            mapCount.set(1);
            return 1;
        }
        return mapCount.incrementAndGet();
    }

    public int incrementAndGetBlockPlaceCount(long now) {
        if (now - lastBlockPlaceTime.get() > 1000) {
            lastBlockPlaceTime.set(now);
            blockPlaceCount.set(1);
            return 1;
        }
        return blockPlaceCount.incrementAndGet();
    }

    public boolean checkAndSetEntityInteractDelay(long now, long ms) {
        if (now - lastEntityInteractTime.get() < ms) return true;
        lastEntityInteractTime.set(now);
        return false;
    }

    public int incrementAndGetPrinterCount(long now) {
        if (now - lastPrinterTime.get() > 1000) {
            lastPrinterTime.set(now);
            printerCount.set(1);
            return 1;
        }
        return printerCount.incrementAndGet();
    }

    public int incrementAndGetRespawnCount(long now) {
        if (now - lastRespawnTime.get() > 1000) {
            lastRespawnTime.set(now);
            respawnCount.set(1);
            return 1;
        }
        return respawnCount.incrementAndGet();
    }

    public int incrementAndGetTabCount(long now) {
        if (now - lastTabTime.get() > 1000) {
            lastTabTime.set(now);
            tabCount.set(1);
            return 1;
        }
        return tabCount.incrementAndGet();
    }

    public int incrementAndGetChannelRegisterCount(int amount, long now) {
        if (now - lastChannelRegisterTime.get() > 60000) {
            lastChannelRegisterTime.set(now);
            recentChannelRegisters.set(amount);
            return amount;
        }
        return recentChannelRegisters.addAndGet(amount);
    }

    public int incrementAndGetRecipeCount(long now) {
        if (now - lastRecipeTime.get() > 1000) {
            lastRecipeTime.set(now);
            recipeCount.set(1);
            return 1;
        }
        return recipeCount.incrementAndGet();
    }

    public int incrementAndGetQuickMoveCount(long now) {
        if (now - lastQuickMoveTime.get() > 1000) {
            lastQuickMoveTime.set(now);
            quickMoveCount.set(1);
            return 1;
        }
        return quickMoveCount.incrementAndGet();
    }

    public Player getPlayer() { return player; }
    public UUID getUuid() { return uuid; }
    public int getEntityId() { return entityId; }
    public ProtocolStateMachine getStateMachine() { return stateMachine; }
    public HybridRateLimiter getRateLimiter() { return rateLimiter; }
    public KeepAliveTracker getKeepAliveTracker() { return keepAliveTracker; }

    public boolean isInventoryOpen() { return inventoryState.isOpen; }
    public int getOpenWindowId() { return inventoryState.windowId; }
    public void setInventoryState(boolean isOpen, int windowId) {
        this.inventoryState = isOpen ? new InventoryState(true, windowId) : CLOSED_STATE;
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