package com.mertout.lightguard.listeners;

import com.mertout.lightguard.LightGuard;
import org.bukkit.Material;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.*;

public class MechanicListener implements Listener {
    private final LightGuard plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private static final Set<Material> NOISY_BLOCKS = EnumSet.of(
            Material.NOTE_BLOCK, Material.BELL,
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
            Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
            Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR, Material.IRON_TRAPDOOR,
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR,
            Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.CRIMSON_DOOR, Material.WARPED_DOOR,
            Material.IRON_DOOR, Material.LEVER, Material.STONE_BUTTON, Material.OAK_BUTTON,
            Material.REPEATER, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR
    );

    public MechanicListener(LightGuard plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) { if (plugin.getConfig().getBoolean("mechanics.nether-portal-delay") && checkCooldown(e.getPlayer().getUniqueId(), 2000)) e.setCancelled(true);    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof org.bukkit.entity.Player) {
            org.bukkit.util.Vector velocity = event.getEntity().getVelocity();
            double speed = velocity.length();
            double maxSpeed = plugin.getConfig().getDouble("mechanics.max-arrow-velocity", 15.0);

            if (speed > maxSpeed || !Double.isFinite(speed)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType().name().contains("PIGLIN")) {
            if (plugin.getConfig().getBoolean("mechanics.disable-piglin-trading")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        plugin.getPlayerDataManager().getData(event.getPlayer().getUniqueId()).setLastTeleportTime(System.currentTimeMillis());
        if (plugin.getConfig().getBoolean("mechanics.close-inventory-on-teleport")) {
            event.getPlayer().closeInventory();
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onBlockInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            int x = event.getClickedBlock().getX() >> 4;
            int z = event.getClickedBlock().getZ() >> 4;
            if (!event.getClickedBlock().getWorld().isChunkLoaded(x, z)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityPortal(org.bukkit.event.entity.EntityPortalEvent event) {
        org.bukkit.entity.Entity entity = event.getEntity();
        if (plugin.getConfig().getBoolean("mechanics.prevent-mule-dupe")) {
            if (entity instanceof org.bukkit.entity.ChestedHorse) {
                event.setCancelled(true);
                return;
            }
        }
        if (entity instanceof org.bukkit.entity.Projectile) {
            event.setCancelled(true);
            entity.remove();
        }
    }

    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onFallingBlockForm(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (event.getEntityType() == org.bukkit.entity.EntityType.FALLING_BLOCK) {
            if (plugin.getConfig().getBoolean("mechanics.limit-falling-blocks")) {
                org.bukkit.Chunk chunk = event.getBlock().getChunk();
                int limit = plugin.getConfig().getInt("mechanics.max-falling-blocks-per-chunk", 20);
                int count = 0;
                for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
                    if (entity.getType() == org.bukkit.entity.EntityType.FALLING_BLOCK) {
                        count++;
                    }
                }
                if (count >= limit) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onNetherBuild(org.bukkit.event.block.BlockPlaceEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.prevent-nether-roof")) {
            if (event.getBlock().getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER) {
                if (event.getBlock().getY() >= 127) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§cNether tavanına blok koyamazsın!");
                }
            }
        }
    }

    @EventHandler
    public void onShear(PlayerShearEntityEvent e) {
        if (plugin.getConfig().getBoolean("mechanics.shears-cooldown") && checkCooldown(e.getPlayer().getUniqueId(), 500)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;

        Material type = e.getClickedBlock().getType();
        UUID uuid = e.getPlayer().getUniqueId();

        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.ENDER_CHEST) {
            long delay = plugin.getConfig().getLong("mechanics.interact-container-delay", 200);
            if (checkCooldown(uuid, delay)) {
                e.setCancelled(true);
                return;
            }
        }

        if (NOISY_BLOCKS.contains(type)) {
            if (checkCooldown(uuid, 100)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBedEnter(org.bukkit.event.player.PlayerBedEnterEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.bed-duplication")) {
            event.getPlayer().closeInventory();
        }
    }

    @EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.self-damage")) {
            if (event.getDamager().equals(event.getEntity())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        if (name.length() > 16) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cInvalid Username Length");
            return;
        }
        if (!name.matches("[a-zA-Z0-9_]+")) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cInvalid Characters in Username");
            return;
        }
        if (event.getAddress() == null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cNull IP Address");
            return;
        }
    }

    @EventHandler
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.null-chunk")) {
            int x = event.getTo().getBlockX() >> 4;
            int z = event.getTo().getBlockZ() >> 4;
            if (!event.getTo().getWorld().isChunkLoaded(x, z)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-mule-dupe")) return;
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof InventoryHolder) {
                closeInventoryForViewers(entity);
            }
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-mule-dupe")) return;
        Entity entity = event.getEntity();
        if (entity instanceof InventoryHolder) {
            closeInventoryForViewers(entity);
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.close-inventory-on-teleport")) return;
        Player player = event.getPlayer();
        Inventory topInv = player.getOpenInventory().getTopInventory();
        if (topInv != null && topInv.getType() != InventoryType.CRAFTING) {
            player.closeInventory();
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onEntityTeleport(org.bukkit.event.entity.EntityTeleportEvent event) {
        if (event.getEntity().getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
            if (!(event.getEntity() instanceof Player)) {
                event.setCancelled(true);
            }
        }
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDispense(org.bukkit.event.block.BlockDispenseEvent event) {
        org.bukkit.block.Block block = event.getBlock();
        if (block.getType() == Material.DISPENSER || block.getType() == Material.DROPPER) {
            int y = block.getY();
            if (y <= 0 || y >= block.getWorld().getMaxHeight() - 1) {
                event.setCancelled(true);
            }
        }
    }
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (plugin.getConfig().getBoolean("mechanics.break-close-inventory") && e.getPlayer().getOpenInventory().getType() != InventoryType.CRAFTING) e.getPlayer().closeInventory();
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-portal-break")) return;
        Material to = event.getToBlock().getType();
        if (to == Material.NETHER_PORTAL || to == Material.END_PORTAL) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.LOW, ignoreCancelled = true)
    public void onStructureGrow(org.bukkit.event.world.StructureGrowEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-portal-break")) return;
        for (org.bukkit.block.BlockState newState : event.getBlocks()) {
            org.bukkit.block.Block existingBlock = newState.getBlock();
            Material type = existingBlock.getType();
            if (type == Material.END_PORTAL || type == Material.END_PORTAL_FRAME || type == Material.END_GATEWAY || type == Material.NETHER_PORTAL) {
                event.setCancelled(true);
                return;
            }
        }
    }
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-mule-dupe")) return;
        if (!(event.getPlayer() instanceof Player)) return;
        if (event.getInventory().getHolder() instanceof ChestedHorse) {
            ChestedHorse mule = (ChestedHorse) event.getInventory().getHolder();
            Player player = (Player) event.getPlayer();
            if (!mule.getWorld().equals(player.getWorld()) || mule.getLocation().distanceSquared(player.getLocation()) > 64) {
                event.setCancelled(true);
                player.sendMessage("§cGüvenlik: Uzaktaki binek envanteri açılamaz.");
                return;
            }
            if (!mule.getLocation().getChunk().isLoaded()) {
                event.setCancelled(true);
            }
        }
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-piston-crash")) return;
        for (org.bukkit.block.Block block : event.getBlocks()) {
            switch (block.getType()) {
                case CHEST: case TRAPPED_CHEST: case SPAWNER: case FURNACE: case BLAST_FURNACE:
                case DISPENSER: case DROPPER: case HOPPER: case BEACON: case ENDER_CHEST: case ENCHANTING_TABLE:
                    event.setCancelled(true);
                    return;
            }
        }
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-piston-crash")) return;
        for (org.bukkit.block.Block block : event.getBlocks()) {
            switch (block.getType()) {
                case CHEST: case TRAPPED_CHEST: case SPAWNER: case FURNACE: case BLAST_FURNACE:
                case DISPENSER: case DROPPER: case HOPPER: case BEACON: case ENDER_CHEST: case ENCHANTING_TABLE:
                    event.setCancelled(true);
                    return;
            }
        }
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.trapdoor-rail-redstone")) return;

        Material type = event.getBlock().getType();
        String name = type.name();

        if (name.contains("TRAPDOOR") || name.contains("RAIL") ||
                type == Material.COMPARATOR || type == Material.OBSERVER) {

            long key = ((long) event.getBlock().getX() & 0xFFFFFFF) | (((long) event.getBlock().getZ() & 0xFFFFFFF) << 28);

            if (event.getOldCurrent() > 0 && event.getNewCurrent() > 0) {
                if (Math.random() > 0.8) {
                    event.setNewCurrent(0);
                }
            }
        }
    }
    @EventHandler
    public void onMerchantOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.prevent-invalid-trade")) {
            if (event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.MERCHANT) {
                event.getPlayer().setItemOnCursor(null);
            }
        }
    }
    @EventHandler
    public void onFrameInteract(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.ItemFrame) {
            if (!event.getEntity().isValid()) {
                event.setCancelled(true);
            }
        }
    }

    private boolean checkCooldown(UUID uuid, long ms) {
        long now = System.currentTimeMillis();
        if (now - cooldowns.getOrDefault(uuid, 0L) < ms) return true;
        cooldowns.put(uuid, now);
        return false;
    }

    private void closeInventoryForViewers(Entity entity) {
        if (entity instanceof InventoryHolder) {
            Inventory inv = ((InventoryHolder) entity).getInventory();
            if (inv != null && !inv.getViewers().isEmpty()) {
                new ArrayList<>(inv.getViewers()).forEach(org.bukkit.entity.HumanEntity::closeInventory);
            }
        }
    }
}