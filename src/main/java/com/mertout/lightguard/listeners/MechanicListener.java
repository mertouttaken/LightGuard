package com.mertout.lightguard.listeners;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;
import com.mertout.lightguard.utils.GeyserUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
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
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class MechanicListener implements Listener {

    private final LightGuard plugin;

    /** Cooldowns are tracked per feature, not just per player: a single shared timestamp would let
     *  unrelated mechanics block each other (opening a chest suppressing portal travel for 2s). */
    private enum Cooldown { PORTAL, SHEARS, CONTAINER, NOISY_BLOCK }

    private static final Cooldown[] COOLDOWNS = Cooldown.values();

    private final Map<UUID, long[]> cooldowns = new ConcurrentHashMap<>();

    private volatile boolean netherPortalDelay, shearsCooldown, preventMuleDupe, preventBedCropDupe;
    private volatile boolean closeInventoryOnTeleport, disablePiglinTrading, limitFallingBlocks;
    private volatile boolean preventNetherRoof, preventDispenserSpam, bedDuplication, selfDamage;
    private volatile boolean nullChunk, preventEndTeleportExploits, breakCloseInventory;
    private volatile boolean preventPortalBreak, preventPistonCrash, trapdoorRailRedstone;
    private volatile boolean preventInvalidTrade, preventAnvilDupe, preventFurnaceDupe, preventHopperDupe;
    private volatile long containerDelay;
    private volatile double maxArrowVelocity;
    private volatile int maxFallingBlocksPerChunk;

    private final Cache<String, Long> dispenserCooldowns = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.SECONDS)
            .build();

    // Derived from the enum rather than hand-listed: the previous literal list was written for
    // 1.16.5 and silently missed every wood type added since (mangrove, cherry, bamboo, copper),
    // along with all but two of the button variants.
    private static final Set<Material> NOISY_BLOCKS = buildNoisyBlocks();

    private static Set<Material> buildNoisyBlocks() {
        Set<Material> set = EnumSet.of(
                Material.NOTE_BLOCK, Material.BELL, Material.LEVER,
                Material.REPEATER, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR
        );
        for (Material m : Material.values()) {
            if (!m.isBlock()) continue;
            String name = m.name();
            if (name.endsWith("_TRAPDOOR") || name.endsWith("_DOOR") || name.endsWith("_BUTTON")) {
                set.add(m);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    private static final Set<Material> CROP_BLOCKS = EnumSet.of(
            Material.WHEAT, Material.POTATOES, Material.CARROTS, Material.NETHER_WART, Material.BEETROOTS
    );

    private static final Set<Material> DISPENSER_SPAM_ITEMS = EnumSet.of(
            Material.SHEARS, Material.WATER_BUCKET, Material.LAVA_BUCKET, Material.FLINT_AND_STEEL, Material.FIRE_CHARGE
    );

    // Block entities a piston must not shove. Shulker boxes are deliberately absent: unlike every
    // entry here they are movable in vanilla, and farms rely on that.
    private static final Set<Material> TILE_ENTITIES = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.BARREL,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.DROPPER, Material.DISPENSER, Material.HOPPER, Material.CRAFTER,
            Material.BEACON, Material.SPAWNER, Material.LECTERN,
            Material.CHISELED_BOOKSHELF, Material.DECORATED_POT,
            Material.BREWING_STAND, Material.ENCHANTING_TABLE,
            Material.VAULT, Material.TRIAL_SPAWNER
    );

    public MechanicListener(LightGuard plugin) {
        this.plugin = plugin;
        loadConfig();

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, long[]>> cdIter = cooldowns.entrySet().iterator();
            while (cdIter.hasNext()) {
                long[] stamps = cdIter.next().getValue();
                long newest = 0L;
                synchronized (stamps) {
                    for (long stamp : stamps) {
                        if (stamp > newest) newest = stamp;
                    }
                }
                if (now - newest > 300000) cdIter.remove();
            }
        }, 1200L, 1200L);
    }

    /**
     * Mechanic toggles are cached rather than read per event: these handlers run on the main
     * thread for every move, interact and redstone tick, and each getConfig() call is a
     * string-keyed map lookup. Refreshed by ConfigManager.reload().
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        this.netherPortalDelay = config.getBoolean("mechanics.nether-portal-delay");
        this.shearsCooldown = config.getBoolean("mechanics.shears-cooldown");
        this.containerDelay = config.getLong("mechanics.interact-container-delay", 300);
        this.preventMuleDupe = config.getBoolean("mechanics.prevent-mule-dupe");
        this.preventBedCropDupe = config.getBoolean("mechanics.prevent-bed-crop-dupe", true);
        this.closeInventoryOnTeleport = config.getBoolean("mechanics.close-inventory-on-teleport", true);
        this.disablePiglinTrading = config.getBoolean("mechanics.disable-piglin-trading");
        this.maxArrowVelocity = config.getDouble("mechanics.max-arrow-velocity", 15.0);
        this.limitFallingBlocks = config.getBoolean("mechanics.limit-falling-blocks");
        this.maxFallingBlocksPerChunk = config.getInt("mechanics.max-falling-blocks-per-chunk", 20);
        this.preventNetherRoof = config.getBoolean("mechanics.prevent-nether-roof");
        this.preventDispenserSpam = config.getBoolean("mechanics.prevent-dispenser-spam", true);
        this.bedDuplication = config.getBoolean("mechanics.bed-duplication");
        this.selfDamage = config.getBoolean("mechanics.self-damage");
        this.nullChunk = config.getBoolean("mechanics.null-chunk", true);
        this.preventEndTeleportExploits = config.getBoolean("mechanics.prevent-end-teleport-exploits", true);
        this.breakCloseInventory = config.getBoolean("mechanics.break-close-inventory");
        this.preventPortalBreak = config.getBoolean("mechanics.prevent-portal-break");
        this.preventPistonCrash = config.getBoolean("mechanics.prevent-piston-crash", true);
        this.trapdoorRailRedstone = config.getBoolean("mechanics.trapdoor-rail-redstone");
        this.preventInvalidTrade = config.getBoolean("mechanics.prevent-invalid-trade");
        this.preventAnvilDupe = config.getBoolean("mechanics.prevent-anvil-dupe", true);
        this.preventFurnaceDupe = config.getBoolean("mechanics.prevent-furnace-dupe", true);
        this.preventHopperDupe = config.getBoolean("mechanics.prevent-hopper-dupe", true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPortal(PlayerPortalEvent e) {
        if (netherPortalDelay && checkCooldown(e.getPlayer().getUniqueId(), Cooldown.PORTAL, 2000)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getProtocolManager().add(player);
        ProtocolPlayer pp = plugin.getProtocolManager().get(player.getUniqueId());
        plugin.getPacketInjector().injectPlayer(player, pp);

        if (pp != null) {
            pp.setCurrentWorldName(player.getWorld().getName());
            pp.setBedrock(GeyserUtil.isBedrockPlayer(player));
            // "lg.admin" is the node declared in plugin.yml; "lightguard.admin" is a legacy alias.
            pp.setAdmin(player.isOp() || player.hasPermission("lg.admin") || player.hasPermission("lightguard.admin"));
            pp.setCreativeOrSpectator(player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR);
            pp.setAllowedFlight(player.getAllowFlight());
            pp.setHoldingBook(isHoldingBook(player));
            pp.setDead(player.isDead());
        }
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

        // Clears the radar *and* the per-player safe-mode state; clearing only the radar leaked
        // an entry per player in SafeModeManager's two maps.
        plugin.getDispatcher().clearViolations(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        ProtocolPlayer pp = plugin.getProtocolManager().get(event.getEntity().getUniqueId());
        if (pp != null) pp.setGliding(event.isGliding());
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
            if (speed > (maxArrowVelocity * maxArrowVelocity) || !Double.isFinite(speed)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        EntityType type = event.getRightClicked().getType();
        if ((type == EntityType.PIGLIN || type == EntityType.PIGLIN_BRUTE) && disablePiglinTrading) {
            event.setCancelled(true);
        }
    }

    /** Single entry point for right-click handling; three separate listeners each paid their own
     *  dispatch on every interaction. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        UUID uuid = event.getPlayer().getUniqueId();
        Material type = clicked.getType();

        if (preventBedCropDupe && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item != null && item.getType().name().endsWith("_BED") && isNextToCrop(clicked, event)) {
                event.setCancelled(true);
                return;
            }
        }

        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.ENDER_CHEST) {
            if (checkCooldown(uuid, Cooldown.CONTAINER, containerDelay)) {
                event.setCancelled(true);
                return;
            }
        }

        if (NOISY_BLOCKS.contains(type) && checkCooldown(uuid, Cooldown.NOISY_BLOCK, 100)) {
            event.setCancelled(true);
        }
    }

    private boolean isNextToCrop(Block clicked, PlayerInteractEvent event) {
        int baseX = clicked.getX() + event.getBlockFace().getModX();
        int baseY = clicked.getY() + event.getBlockFace().getModY();
        int baseZ = clicked.getZ() + event.getBlockFace().getModZ();
        World world = clicked.getWorld();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (CROP_BLOCKS.contains(world.getBlockAt(baseX + x, baseY + y, baseZ + z).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        ProtocolPlayer pp = plugin.getProtocolManager().get(event.getPlayer().getUniqueId());
        if (pp != null) pp.markTeleport();

        if (closeInventoryOnTeleport) {
            event.getPlayer().closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPortal(EntityPortalEvent event) {
        Entity entity = event.getEntity();
        if (preventMuleDupe && (entity instanceof ChestedHorse || (entity instanceof Vehicle && entity instanceof InventoryHolder))) {
            event.setCancelled(true);
            return;
        }
        if (limitFallingBlocks && entity.getType() == EntityType.FALLING_BLOCK) {
            event.setCancelled(true);
            entity.remove();
            return;
        }
        if (preventEndTeleportExploits && entity instanceof Item) {
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
        if (event.getEntityType() == EntityType.FALLING_BLOCK && limitFallingBlocks) {
            Chunk chunk = event.getBlock().getChunk();
            int limit = maxFallingBlocksPerChunk;
            int count = 0;
            for (Entity entity : chunk.getEntities()) {
                if (entity.getType() == EntityType.FALLING_BLOCK) count++;
            }
            if (count >= limit) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNetherBuild(BlockPlaceEvent event) {
        if (preventNetherRoof) {
            if (event.getBlock().getWorld().getEnvironment() == World.Environment.NETHER && event.getBlock().getY() >= 127) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onShear(PlayerShearEntityEvent e) {
        if (shearsCooldown && checkCooldown(e.getPlayer().getUniqueId(), Cooldown.SHEARS, 500)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (bedDuplication) event.getPlayer().closeInventory();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (selfDamage && event.getDamager().equals(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // Floodgate prefixes Bedrock usernames (default "."), and Bedrock gamertags contain spaces,
    // so a strict Java-only pattern would reject every Bedrock player before Geyser support
    // could ever apply to them.
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_.*\\- ]+$");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        if (name.isEmpty() || name.length() > 16 || !VALID_USERNAME.matcher(name).matches()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cInvalid Username");
            return;
        }
        if (event.getAddress() == null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cNull IP Address");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (!nullChunk) return;

        Location to = event.getTo();
        if (to == null) return;

        if (!plugin.getWorldChunkManager().isChunkLoadedAsynchronously(
                to.getWorld().getName(), to.getBlockX() >> 4, to.getBlockZ() >> 4)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!preventMuleDupe) return;
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof InventoryHolder) closeInventoryForViewers(entity);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!preventMuleDupe) return;
        if (event.getEntity() instanceof InventoryHolder) closeInventoryForViewers(event.getEntity());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (!preventEndTeleportExploits) return;

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
        Material type = block.getType();

        if (type == Material.DISPENSER || type == Material.DROPPER) {
            int y = block.getY();
            // World floor is -64 since 1.18; the old "y <= 0" test disabled every dispenser
            // in the bottom 64 layers of the overworld.
            if (y <= block.getWorld().getMinHeight() || y >= block.getWorld().getMaxHeight() - 1) {
                event.setCancelled(true);
                return;
            }
        }

        if (preventDispenserSpam && DISPENSER_SPAM_ITEMS.contains(event.getItem().getType())) {
            if (checkDispenserCooldown(block.getLocation(), 500)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (breakCloseInventory && e.getPlayer().getOpenInventory().getType() != InventoryType.CRAFTING) {
            e.getPlayer().closeInventory();
        }
        if (e.getBlock().getState() instanceof InventoryHolder) {
            InventoryHolder holder = (InventoryHolder) e.getBlock().getState();
            Inventory inv = holder.getInventory();
            if (inv != null && !inv.getViewers().isEmpty()) {
                new ArrayList<>(inv.getViewers()).forEach(org.bukkit.entity.HumanEntity::closeInventory);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (!preventPortalBreak) return;
        Material to = event.getToBlock().getType();
        if (to == Material.NETHER_PORTAL || to == Material.END_PORTAL) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onStructureGrow(org.bukkit.event.world.StructureGrowEvent event) {
        if (!preventPortalBreak) return;
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

        if (preventMuleDupe && event.getInventory().getHolder() instanceof ChestedHorse) {
            ChestedHorse mule = (ChestedHorse) event.getInventory().getHolder();
            if (!mule.getWorld().equals(player.getWorld()) || mule.getLocation().distanceSquared(player.getLocation()) > 64 || !mule.getLocation().getChunk().isLoaded()) {
                event.setCancelled(true);
                return;
            }
        }

        if (preventInvalidTrade && event.getInventory().getType() == InventoryType.MERCHANT) {
            event.getPlayer().setItemOnCursor(null);
            if (!event.getInventory().getViewers().isEmpty()) {
                for (HumanEntity viewer : event.getInventory().getViewers()) {
                    if (!viewer.equals(player)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        ProtocolPlayer pp = plugin.getProtocolManager().get(player.getUniqueId());
        if (pp != null && event.getInventory().getType() == InventoryType.ANVIL) {
            pp.setAnvilOpen(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!preventPistonCrash) return;
        for (Block block : event.getBlocks()) {
            if (TILE_ENTITIES.contains(block.getType())) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!preventPistonCrash) return;
        for (Block block : event.getBlocks()) {
            if (TILE_ENTITIES.contains(block.getType())) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        if (!trapdoorRailRedstone) return;

        Material type = event.getBlock().getType();
        String name = type.name();

        if (name.contains("TRAPDOOR") || name.contains("RAIL") || type == Material.COMPARATOR || type == Material.OBSERVER) {
            if (event.getOldCurrent() > 0 && event.getNewCurrent() > 0 && Math.random() > 0.8) {
                event.setNewCurrent(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getSlotType() == InventoryType.SlotType.RESULT) {
            InventoryType invType = event.getInventory().getType();
            ClickType click = event.getClick();

            if (invType == InventoryType.ANVIL) {
                if (preventAnvilDupe && (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT || click == ClickType.NUMBER_KEY || click == ClickType.DOUBLE_CLICK)) {
                    event.setCancelled(true);
                }
            } else if (invType == InventoryType.FURNACE || invType == InventoryType.BLAST_FURNACE || invType == InventoryType.SMOKER) {
                if (preventFurnaceDupe && (click == ClickType.NUMBER_KEY || click == ClickType.DOUBLE_CLICK)) {
                    event.setCancelled(true);
                }
            } else if (invType == InventoryType.GRINDSTONE || invType == InventoryType.STONECUTTER || invType == InventoryType.SMITHING || invType == InventoryType.CARTOGRAPHY || invType == InventoryType.LOOM || invType == InventoryType.CRAFTER) {
                if (click == ClickType.NUMBER_KEY || click == ClickType.DOUBLE_CLICK) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCrafterCraft(org.bukkit.event.block.CrafterCraftEvent event) {
        Block block = event.getBlock();
        if (checkDispenserCooldown(block.getLocation(), 200)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (!preventHopperDupe) return;

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

    private static final Set<Material> BOOK_ITEMS = EnumSet.of(
            Material.BOOK, Material.WRITABLE_BOOK, Material.WRITTEN_BOOK,
            Material.ENCHANTED_BOOK, Material.KNOWLEDGE_BOOK
    );

    /** Explicit set: the old name().contains("BOOK") test also matched BOOKSHELF. */
    private boolean isHoldingBook(Player player) {
        return BOOK_ITEMS.contains(player.getInventory().getItemInMainHand().getType())
                || BOOK_ITEMS.contains(player.getInventory().getItemInOffHand().getType());
    }

    /** @return true if the action is still on cooldown and should be cancelled. */
    private boolean checkCooldown(UUID uuid, Cooldown type, long ms) {
        long[] stamps = cooldowns.computeIfAbsent(uuid, k -> new long[COOLDOWNS.length]);
        long now = System.currentTimeMillis();

        synchronized (stamps) {
            if (now - stamps[type.ordinal()] < ms) return true;
            stamps[type.ordinal()] = now;
        }
        return false;
    }

    private boolean checkDispenserCooldown(Location loc, long ms) {
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        if (dispenserCooldowns.getIfPresent(key) != null) return true;
        dispenserCooldowns.put(key, System.currentTimeMillis());
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