package com.mertout.lightguard.listeners;

import com.mertout.lightguard.LightGuard;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MechanicListener implements Listener {
    private final LightGuard plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public MechanicListener(LightGuard plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (plugin.getConfig().getBoolean("mechanics.nether-portal-delay") && checkCooldown(e.getPlayer().getUniqueId(), 2000)) e.setCancelled(true);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        // Fırlatılan şey bir canlı tarafından mı atıldı? (Dispenser değil)
        if (event.getEntity().getShooter() instanceof org.bukkit.entity.Player) {

            // Hız vektörünü al
            org.bukkit.util.Vector velocity = event.getEntity().getVelocity();
            double speed = velocity.length();

            // Config'den limiti al (Yoksa 15.0 varsay)
            double maxSpeed = plugin.getConfig().getDouble("mechanics.max-arrow-velocity", 15.0);

            // 1. Hız Limiti (Motion Crash)
            // Eğer hız limiti aşıyorsa veya hız NaN/Infinite (Bozuk) ise
            if (speed > maxSpeed || !Double.isFinite(speed)) {
                event.setCancelled(true);
                // İstersen loglayabilirsin
                return;
            }

            // 2. Ender Pearl'e Özel Cooldown (Opsiyonel ama tavsiye edilir)
            // Spamlayarak sunucuyu yormamaları için
            if (event.getEntity() instanceof org.bukkit.entity.EnderPearl) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) event.getEntity().getShooter();
                // 100ms içinde 2. inciyi atarsa engelle (Flood check yetmezse burası tutar)
                // (Bu basit bir örnektir, FloodCheck zaten bunu yapıyor ama mekanik olarak da ekleyebiliriz)
            }
        }
    }

    @EventHandler
    public void onEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType().name().contains("PIGLIN")) { // Piglin Trade
            if (plugin.getConfig().getBoolean("mechanics.disable-piglin-trading")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.close-inventory-on-teleport")) {
            event.getPlayer().closeInventory();
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onBlockInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            // Blokun olduğu chunk yüklü değilse işlemi iptal et
            if (!event.getClickedBlock().getChunk().isLoaded()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityPortal(org.bukkit.event.entity.EntityPortalEvent event) {
        org.bukkit.entity.Entity entity = event.getEntity();

        // 1. Katır/Lama/At Portal Koruması (Dupe Önlemi)
        if (plugin.getConfig().getBoolean("mechanics.prevent-mule-dupe")) {
            if (entity instanceof org.bukkit.entity.ChestedHorse) { // Sandıklı binekler
                event.setCancelled(true);
                return;
            }
        }

        // 2. Projectile Portal Koruması (Lag Önlemi)
        // Oklar portaldan geçerse diğer tarafta birikir ve sunucuyu kasar.
        if (entity instanceof org.bukkit.entity.Projectile) {
            event.setCancelled(true); // Oklar portaldan geçemez, yok olur.
            entity.remove(); // Entity'yi sil
        }
    }
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onFallingBlockForm(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        // Sadece blok düşen bir entity'ye dönüşüyorsa (FALLING_BLOCK)
        if (event.getEntityType() == org.bukkit.entity.EntityType.FALLING_BLOCK) {

            if (plugin.getConfig().getBoolean("mechanics.limit-falling-blocks")) {
                org.bukkit.Chunk chunk = event.getBlock().getChunk();
                int limit = plugin.getConfig().getInt("mechanics.max-falling-blocks-per-chunk", 20);

                // O chunk'taki düşen blokları say
                int count = 0;
                for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
                    if (entity.getType() == org.bukkit.entity.EntityType.FALLING_BLOCK) {
                        count++;
                    }
                }

                // Limit aşıldıysa işlemi iptal et (Blok düşmez, yerinde kalır veya kırılır)
                if (count >= limit) {
                    event.setCancelled(true);
                    // İstersen bloğu direkt silebilirsin:
                    // event.getBlock().setType(org.bukkit.Material.AIR);
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

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.CHEST) {
            if (checkCooldown(e.getPlayer().getUniqueId(), plugin.getConfig().getLong("mechanics.interact-container-delay", 100))) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBedEnter(org.bukkit.event.player.PlayerBedEnterEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.bed_duplication")) {
            // Yatağa girerken envanter işlemleri bazen dupe yapar, bunu engellemek için
            // yatağa girerken bir anlık envanter kilitlenebilir veya inventory kapatılır.
            event.getPlayer().closeInventory();
        }
    }

    @EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.self_damage")) {
            // Kendi kendine vurarak sunucuyu yorma (EntitySelfDamage packet spam)
            if (event.getDamager().equals(event.getEntity())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();

        // 1. Oversized Username (16 karakter sınırı)
        if (name.length() > 16) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cInvalid Username Length");
            return;
        }

        // 2. Invalid Characters (Regex)
        // Sadece a-z, A-Z, 0-9 ve _ (alt çizgi) izin ver
        if (!name.matches("[a-zA-Z0-9_]+")) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cInvalid Characters in Username");
            return;
        }

        // 3. Null Address Check (Bot koruması)
        if (event.getAddress() == null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cNull IP Address");
            return;
        }
    }

    @EventHandler
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.null_chunk")) {
            if (!event.getTo().getChunk().isLoaded()) {
                event.setCancelled(true); // Yüklenmemiş chunk'a girmeyi engelle
            }
        }
    }

    @EventHandler
    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-mule-dupe")) return;

        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof org.bukkit.inventory.InventoryHolder) {
                org.bukkit.inventory.Inventory inventory = ((org.bukkit.inventory.InventoryHolder) entity).getInventory();
                // Bu envantere bakan oyuncuları bul ve kapat
                if (inventory != null && !inventory.getViewers().isEmpty()) {
                    // Listeyi kopyalayarak işlem yap (ConcurrentModification hatası olmasın)
                    new java.util.ArrayList<>(inventory.getViewers()).forEach(org.bukkit.entity.HumanEntity::closeInventory);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTeleport(org.bukkit.event.entity.EntityTeleportEvent event) {
        // Sadece END dünyasında
        if (event.getEntity().getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
            // Eğer ışınlanan varlık bir oyuncu değilse (Mob, Item vb.) ve Gateway'e giriyorsa
            // (Basitçe End'deki entity teleportlarını kısıtlayarak crashi önlüyoruz)
            // Daha spesifik olmak gerekirse: Gateway bloğuna girip girmediğine bakılır ama bu genel çözüm de iş görür.
            if (!(event.getEntity() instanceof Player)) {
                // Genellikle crash yapanlar vagonlar veya itemlerdir.
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDispense(org.bukkit.event.block.BlockDispenseEvent event) {
        org.bukkit.block.Block block = event.getBlock();
        if (block.getType() == Material.DISPENSER || block.getType() == Material.DROPPER) {
            int y = block.getY();

            // Yüksekliğe göre tehlike analizi
            // Y=0 veya daha altı (Void) ve Y=MaxHeight ise engelle
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

    // ➤ GÜNCELLENEN: Portal Break Fix (Mantar/Ağaç Büyütme)
    @EventHandler(priority = org.bukkit.event.EventPriority.LOW, ignoreCancelled = true)
    public void onStructureGrow(org.bukkit.event.world.StructureGrowEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-portal-break")) return;

        // Büyüyen yapının (Ağaç/Mantar) kaplayacağı blokları kontrol et
        for (org.bukkit.block.BlockState newState : event.getBlocks()) {
            // O konumda ŞU AN ne var?
            org.bukkit.block.Block existingBlock = newState.getBlock();
            Material type = existingBlock.getType();

            // Eğer büyüdüğü yerde Portal, Çerçeve veya Gateway varsa iptal et
            if (type == Material.END_PORTAL ||
                    type == Material.END_PORTAL_FRAME ||
                    type == Material.END_GATEWAY ||
                    type == Material.NETHER_PORTAL) {

                event.setCancelled(true);
                return; // Tek bir blok bile portala değiyorsa işlemi durdur
            }
        }
    }

    // ➤ YENİ: Mule Dupe Fix (Yüklenmemiş chunk'taki katırı açma)
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-mule-dupe")) return;
        if (!(event.getPlayer() instanceof Player)) return;

        if (event.getInventory().getHolder() instanceof ChestedHorse) {
            ChestedHorse mule = (ChestedHorse) event.getInventory().getHolder();
            Player player = (Player) event.getPlayer();

            // Eğer katır ve oyuncu farklı dünyadaysa veya çok uzaksa
            if (!mule.getWorld().equals(player.getWorld()) || mule.getLocation().distanceSquared(player.getLocation()) > 64) {
                event.setCancelled(true);
                player.sendMessage("§cGüvenlik: Uzaktaki binek envanteri açılamaz.");
                return;
            }

            // Eğer katırın olduğu chunk yüklenmemişse
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
                case CHEST:
                case TRAPPED_CHEST:
                case SPAWNER:
                case FURNACE:
                case BLAST_FURNACE:
                case DISPENSER:
                case DROPPER:
                case HOPPER:
                case BEACON:
                case ENDER_CHEST:
                case ENCHANTING_TABLE:
                    event.setCancelled(true);
                    return;
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        if (!plugin.getConfig().getBoolean("mechanics.prevent-piston-crash")) return;

        for (org.bukkit.block.Block block : event.getBlocks()) {
            Material type = block.getType();

            switch (type) {
                case CHEST:
                case TRAPPED_CHEST:
                case SPAWNER:
                case FURNACE:
                case BLAST_FURNACE:
                case DISPENSER:
                case DROPPER:
                case HOPPER:
                case BEACON:
                case ENDER_CHEST:
                case ENCHANTING_TABLE:
                    event.setCancelled(true);
                    return;
            }
        }
    }
    // ➤ YENİ: Merchant (Köylü) Crash Fix
    // Envanter açıldığında eğer bu bir Ticaret (Merchant) ise kontrol et.
    @EventHandler
    public void onMerchantOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (plugin.getConfig().getBoolean("mechanics.prevent-invalid-trade")) {
            if (event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.MERCHANT) {
                // Oyuncu köylü menüsünü açarken elinde (cursor) hayalet item varsa sunucuyu yanıltabilir.
                // Güvenlik için cursor'u temizliyoruz veya senkronize ediyoruz.
                event.getPlayer().setItemOnCursor(null);
            }
        }
    }
    // ➤ YENİ: Item Frame Protection (Frame Crash Fix)
    @EventHandler
    public void onFrameInteract(EntityDamageEvent event) {
        // Item frame koruması: Çok sık vurmayı veya patlatmayı engelleyebiliriz
        // (Burada basit bir örnek, detaylandırılabilir)
        if (event.getEntity() instanceof org.bukkit.entity.ItemFrame) {
            // Frame entitysi geçerli mi kontrol et
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
}