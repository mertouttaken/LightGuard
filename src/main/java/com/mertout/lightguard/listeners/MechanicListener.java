package com.mertout.lightguard.listeners;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MechanicListener implements Listener {

    private final LightGuard plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<Location, Long> dispenserCooldowns = new ConcurrentHashMap<>();

    private static final Set<Material> NOISY_BLOCKS = EnumSet.of(
            Material.NOTE_BLOCK, Material.BELL, Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR,
            Material.BIRCH_TRAPDOOR, Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR,
            Material.DARK_OAK_TRAPDOOR, Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR,
            Material.IRON_TRAPDOOR, Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR,
            Material.JUNGLE_DOOR, Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.CRIMSON_DOOR,
            Material.WARPED_DOOR, Material.IRON_DOOR, Material.LEVER, Material.STONE_BUTTON,
            Material.OAK_BUTTON, Material.REPEATER, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR
    );

    private static final Set<Material> CROP_BLOCKS = EnumSet.of(
            Material.WHEAT, Material.POTATOES, Material.CARROTS, Material.NETHER_WART, Material.BEETROOTS
    );

    private static final Set<Material> DISPENSER_SPAM_ITEMS = EnumSet.of(
            Material.SHEARS, Material.WATER_BUCKET, Material.LAVA_BUCKET, Material.FLINT_AND_STEEL, Material.FIRE_CHARGE
    );

    private static final Set<Material> TILE_ENTITIES = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.FURNACE, Material.BLAST_FURNACE,
            Material.SMOKER, Material.DROPPER, Material.DISPENSER, Material.HOPPER,
            Material.BEACON, Material.SPAWNER
    );

    public MechanicListener(LightGuard plugin) {
        this.plugin = plugin;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();

            Iterator<Map.Entry<Location, Long>> dispIter = dispenserCooldowns.entrySet().iterator();
            while (dispIter.hasNext()) {
                if (now - dispIter.next().getValue() > 60000) dispIter.remove();
            }

            Iterator<Map.Entry<UUID, Long>> cdIter = cooldowns.entrySet().iterator();
            while (cdIter.hasNext()) {
                if (now - cdIter.next().getValue() > 300000) cdIter.remove();
            }
        }, 1200L, 1200L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPortal(PlayerPortalEvent e) {
        if (plugin.getConfig().getBoolean("mechanics.nether-portal-delay") && checkCooldown(e.getPlayer().getUniqueId(), 2000)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoinFirst(PlayerJoinEvent event) {
        plugin.getProtocolManager().add(event.getPlayer());
        ProtocolPlayer pp = plugin.getProtocolManager().get(event.getPlayer().getUniqueId());
        plugin.getPacketInjector().injectPlayer(event.getPlayer(), pp);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cooldowns.remove(uuid);
        ProtocolPlayer pp = plugin.getProtocolManager().get(uuid);
        if (pp != null) {
            pp.getKeepAliveTracker().clear();
        }
        plugin.getProtocolManager().remove(uuid);
        plugin.getPacketInjector().removePlayer(event.getPlayer());

        plugin.getDispatcher().clearViolations(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        ProtocolPlayer pp = plugin.getProtocolManager().get(event.getEntity().getUniqueId());
        if (pp != null) pp.setGliding(event.isGliding());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ProtocolPlayer pp = plugin.getProtocolManager().get(player.getUniqueId());
        if (pp != null) {
            pp.setCurrentWorldName(player.getWorld().getName());
            pp.setAdmin(player.isOp() || player.hasPermission("lightguard.admin"));
            pp.setCreativeOrSpectator(player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR);
            pp.setAllowedFlight(player.getAllowFlight());
            pp.setHoldingBook(isHoldingBook(player));
            pp.setDead(player.isDead());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        ProtocolPlayer pp = plugin.getProtocolManager().get(event.getPlayer().getUniqueId());
        if (pp != null) pp.setCurrentWorldName(event.getPlayer().getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        ProtocolPlayer pp = plugin.getProtocolManager().get(event.getPlayer().getUniqueId());
        if (pp != null) {
            pp.setCreativeOrSpectator(event.getNewGameMode() == GameMode.CREATIVE || event.getNewGameMode() == GameMode.SPECTATOR);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        ProtocolPlayer pp = plugin.getProtocolManager().get(event.getPlayer().getUniqueId());
        if (pp != null) pp.setAllowedFlight(event.isFlying());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        ProtocolPlayer pp = plugin.getProtocolManager().get(event.getPlayer().getUniqueId());
        if (pp != null && event.getInventory().getType() == InventoryType.ANVIL) {
            pp.setAnvilOpen(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        ProtocolPlayer pp = plugin.getProtocolManager().get(event.getEntity().getUniqueId());
        if (pp != null) pp.setDead(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        ProtocolPlayer pp = plugin.getProtocolManager().get(event.getPlayer().getUniqueId());
        if (pp != null) {
            pp.setDead(false);
            pp.markTeleport();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        ProtocolPlayer pp = plugin.getProtocolManager().get(event.getPlayer().getUniqueId());
        if (pp != null) pp.setHoldingBook(isHoldingBook(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            double speed = event.getEntity().getVelocity().lengthSquared();
            double maxSpeed = plugin.getConfig().getDouble("mechanics.max-arrow-velocity", 15.0);
            if (speed > (maxSpeed * maxSpeed) || !Double.isFinite(speed)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        EntityType type = event.getRightClicked().getType();
        if ((type == EntityType.PIGLIN || type == EntityType.PIGLIN_BRUTE) && plugin.getConfig().getBoolean("mechanics.disable-piglin-trading")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBedCropDupe(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-bed-crop-dupe", true)) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();

            if (item != null && item.getType().name().endsWith("_BED")) {
                Block clicked = event.getClickedBlock();
                if (clicked != null) {
                    int baseX = clicked.getX() + event.getBlockFace().getModX();
                    int baseY = clicked.getY() + event.getBlockFace().getModY();
                    int baseZ = clicked.getZ() + event.getBlockFace().getModZ();
                    World world = clicked.getWorld();

                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            for (int z = -1; z <= 1; z++) {
                                if (CROP_BLOCKS.contains(world.getBlockAt(baseX + x, baseY + y, baseZ + z).getType())) {
                                    event.setCancelled(true);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        ProtocolPlayer pp = plugin.getProtocolManager().get(event.getPlayer().getUniqueId());
        if (pp != null) pp.markTeleport();

        if (plugin.getConfig().getBoolean("mechanics.close-inventory-on-teleport", true)) {
            event.getPlayer().closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            int x = event.getClickedBlock().getX() >> 4;
            int z = event.getClickedBlock().getZ() >> 4;
            if (!event.getClickedBlock().getWorld().isChunkLoaded(x, z)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPortal(EntityPortalEvent event) {
        Entity entity = event.getEntity();
        if (plugin.getConfig().getBoolean("mechanics.prevent-mule-dupe") && entity instanceof ChestedHorse) {
            event.setCancelled(true);
            return;
        }
        if (entity instanceof Projectile) {
            event.setCancelled(true);
            entity.remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFallingBlockForm(EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.FALLING_BLOCK && plugin.getConfig().getBoolean("mechanics.limit-falling-blocks")) {
            Chunk chunk = event.getBlock().getChunk();
            int limit = plugin.getConfig().getInt("mechanics.max-falling-blocks-per-chunk", 20);
            int count = 0;
            for (Entity entity : chunk.getEntities()) {
                if (entity.getType() == EntityType.FALLING_BLOCK) count++;
            }
            if (count >= limit) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNetherBuild(BlockPlaceEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.prevent-nether-roof")) {
            if (event.getBlock().getWorld().getEnvironment() == World.Environment.NETHER && event.getBlock().getY() >= 127) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onShear(PlayerShearEntityEvent e) {
        if (plugin.getConfig().getBoolean("mechanics.shears-cooldown") && checkCooldown(e.getPlayer().getUniqueId(), 500)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispenserSpam(BlockDispenseEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-dispenser-spam", true)) return;

        if (DISPENSER_SPAM_ITEMS.contains(event.getItem().getType())) {
            if (checkDispenserCooldown(event.getBlock().getLocation(), 500)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;

        Material type = e.getClickedBlock().getType();
        UUID uuid = e.getPlayer().getUniqueId();

        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.ENDER_CHEST) {
            long delay = plugin.getConfig().getLong("mechanics.interact-container-delay", 300);
            if (checkCooldown(uuid, delay)) {
                e.setCancelled(true);
                return;
            }
        }

        if (NOISY_BLOCKS.contains(type) && checkCooldown(uuid, 100)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.bed-duplication")) event.getPlayer().closeInventory();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.self-damage") && event.getDamager().equals(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        if (name.length() > 16 || !name.matches("^[a-zA-Z0-9_]+$")) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cInvalid Username");
            return;
        }
        if (event.getAddress() == null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cNull IP Address");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.null-chunk", true)) return;

        Location to = event.getTo();
        if (to != null && !plugin.getWorldChunkManager().isChunkLoadedAsynchronously(to.getWorld().getName(), to.getBlockX() >> 4, to.getBlockZ() >> 4)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-mule-dupe")) return;
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof InventoryHolder) closeInventoryForViewers(entity);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-mule-dupe")) return;
        if (event.getEntity() instanceof InventoryHolder) closeInventoryForViewers(event.getEntity());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-end-teleport-exploits", true)) return;

        Entity entity = event.getEntity();
        if (entity instanceof Item || entity instanceof ArmorStand) {
            if (event.getFrom() != null && event.getTo() != null) {
                if (event.getFrom().getWorld().getEnvironment() != World.Environment.THE_END &&
                        event.getTo().getWorld().getEnvironment() == World.Environment.THE_END) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.DISPENSER || block.getType() == Material.DROPPER) {
            int y = block.getY();
            if (y <= 0 || y >= block.getWorld().getMaxHeight() - 1) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.getConfig().getBoolean("mechanics.break-close-inventory") && e.getPlayer().getOpenInventory().getType() != InventoryType.CRAFTING) {
            e.getPlayer().closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-portal-break")) return;
        Material to = event.getToBlock().getType();
        if (to == Material.NETHER_PORTAL || to == Material.END_PORTAL) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onStructureGrow(org.bukkit.event.world.StructureGrowEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-portal-break")) return;
        for (org.bukkit.block.BlockState newState : event.getBlocks()) {
            Material type = newState.getBlock().getType();
            if (type == Material.END_PORTAL || type == Material.END_PORTAL_FRAME || type == Material.END_GATEWAY || type == Material.NETHER_PORTAL) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        if (plugin.getConfig().getBoolean("mechanics.prevent-mule-dupe") && event.getInventory().getHolder() instanceof ChestedHorse) {
            ChestedHorse mule = (ChestedHorse) event.getInventory().getHolder();
            if (!mule.getWorld().equals(player.getWorld()) || mule.getLocation().distanceSquared(player.getLocation()) > 64 || !mule.getLocation().getChunk().isLoaded()) {
                event.setCancelled(true);
                return;
            }
        }

        ProtocolPlayer pp = plugin.getProtocolManager().get(player.getUniqueId());
        if (pp != null && event.getInventory().getType() == InventoryType.ANVIL) {
            pp.setAnvilOpen(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-piston-crash", true)) return;
        for (Block block : event.getBlocks()) {
            if (TILE_ENTITIES.contains(block.getType())) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-piston-crash", true)) return;
        for (Block block : event.getBlocks()) {
            if (TILE_ENTITIES.contains(block.getType())) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.trapdoor-rail-redstone")) return;

        Material type = event.getBlock().getType();
        String name = type.name();

        if (name.contains("TRAPDOOR") || name.contains("RAIL") || type == Material.COMPARATOR || type == Material.OBSERVER) {
            if (event.getOldCurrent() > 0 && event.getNewCurrent() > 0 && Math.random() > 0.8) {
                event.setNewCurrent(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMerchantOpen(InventoryOpenEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.prevent-invalid-trade") && event.getInventory().getType() == InventoryType.MERCHANT) {
            event.getPlayer().setItemOnCursor(null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrameInteract(EntityDamageEvent event) {
        if (event.getEntity() instanceof ItemFrame && !event.getEntity().isValid()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getType() == InventoryType.ANVIL && event.getSlotType() == InventoryType.SlotType.RESULT) {
            if (plugin.getConfig().getBoolean("mechanics.prevent-anvil-dupe", true) && (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) {
                event.setCancelled(true);
            }
        }
        if (event.getInventory().getType() == InventoryType.FURNACE && event.getSlotType() == InventoryType.SlotType.RESULT) {
            if (plugin.getConfig().getBoolean("mechanics.prevent-furnace-dupe", true) && event.getClick() == ClickType.NUMBER_KEY) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-hopper-dupe", true)) return;

        if (event.getSource().getHolder() instanceof StorageMinecart || event.getDestination().getHolder() instanceof StorageMinecart) {
            if (!event.getSource().getLocation().getChunk().isLoaded() || !event.getDestination().getLocation().getChunk().isLoaded()) {
                event.setCancelled(true);
                return;
            }
            if (event.getSource().getHolder() instanceof StorageMinecart && ((StorageMinecart) event.getSource().getHolder()).isDead()) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isHoldingBook(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        return (main != null && main.getType().name().contains("BOOK")) || (off != null && off.getType().name().contains("BOOK"));
    }

    private boolean checkCooldown(UUID uuid, long ms) {
        long now = System.currentTimeMillis();
        if (now - cooldowns.getOrDefault(uuid, 0L) < ms) return true;
        cooldowns.put(uuid, now);
        return false;
    }

    private boolean checkDispenserCooldown(Location loc, long ms) {
        long now = System.currentTimeMillis();
        if (now - dispenserCooldowns.getOrDefault(loc, 0L) < ms) return true;
        dispenserCooldowns.put(loc, now);
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