package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.impl.*;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.Bukkit;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Dispatcher {

    private final LightGuard plugin;

    private final Map<Class<?>, Check[]> packetMap = new HashMap<>();
    private Check[] globalChecksArray;

    private final SafeModeManager safeModeManager;

    private final Map<Class<?>, String> nameCache = new ConcurrentHashMap<>();
    private final Map<Check, String> checkNameCache = new IdentityHashMap<>();

    private final Map<UUID, Map<String, Integer>> violationTracker = new ConcurrentHashMap<>();
    private static final int MAX_VIOLATIONS = 6;

    private final boolean geyserSupport;
    private final boolean silentFailures;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Map<Class<?>, List<Check>> tempPacketMap = new HashMap<>();

    public Dispatcher(LightGuard plugin) {
        this.plugin = plugin;
        this.safeModeManager = new SafeModeManager(plugin);

        this.geyserSupport = plugin.getConfig().getBoolean("settings.sentinel.geyser-support", true);
        this.silentFailures = plugin.getConfig().getBoolean("settings.sentinel.silent-failures", false);

        loadChecks();

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, violationTracker::clear, 6000L, 6000L);
    }

    private void loadChecks() {
        Check[] globals = {
                new PacketSizeCheck(plugin),
                new ProtocolExploitCheck(plugin)
        };
        for (Check c : globals) {
            checkNameCache.put(c, c.getClass().getSimpleName());
        }
        this.globalChecksArray = globals;

        register(PacketPlayInChat.class, new CommandCheck(plugin), new ChatSecurityCheck(plugin));
        register(PacketPlayInTabComplete.class, new TabCheck(plugin));

        register(PacketPlayInWindowClick.class, new ItemExploitCheck(plugin), new WindowCheck(plugin));
        register(PacketPlayInSetCreativeSlot.class, new ItemExploitCheck(plugin), new MapExploitCheck(plugin), new CreativeCheck(plugin));
        register(PacketPlayInItemName.class, new AnvilCheck(plugin));
        register(PacketPlayInHeldItemSlot.class, new HeldItemSlotCheck(plugin));
        register(PacketPlayInBEdit.class, new BookExploitCheck(plugin));

        PositionCheck posCheck = new PositionCheck(plugin);
        register(PacketPlayInFlying.class, posCheck);
        register(PacketPlayInFlying.PacketPlayInPosition.class, posCheck);
        register(PacketPlayInFlying.PacketPlayInLook.class, posCheck);
        register(PacketPlayInFlying.PacketPlayInPositionLook.class, posCheck);
        register(PacketPlayInKeepAlive.class, new KeepAliveCheck(plugin));

        VehicleCheck vehicleCheck = new VehicleCheck(plugin);
        register(PacketPlayInSteerVehicle.class, vehicleCheck);
        register(PacketPlayInVehicleMove.class, vehicleCheck);

        register(PacketPlayInUseItem.class, new BlockPlaceCheck(plugin));
        register(PacketPlayInUpdateSign.class, new SignCheck(plugin));
        register(PacketPlayInBlockDig.class, new NoComCheck(plugin));

        register(PacketPlayInUseEntity.class, new BadPacketCheck(plugin), new EntityCheck(plugin));

        register(PacketPlayInCustomPayload.class, new PayloadCheck(plugin), new BrandCheck(plugin));
        register(PacketPlayInSettings.class, new SettingsCheck(plugin));
        register(PacketPlayInResourcePackStatus.class, new ResourcePackCheck(plugin));
        register(PacketPlayInAutoRecipe.class, new RecipeCheck(plugin));
        register(PacketPlayInRecipeDisplayed.class, new RecipeCheck(plugin));
        register(PacketPlayInAbilities.class, new AbilitiesCheck(plugin));
        register(PacketPlayInClientCommand.class, new ClientActionCheck(plugin));

        for (Map.Entry<Class<?>, List<Check>> entry : tempPacketMap.entrySet()) {
            packetMap.put(entry.getKey(), entry.getValue().toArray(new Check[0]));
        }
        tempPacketMap.clear();
    }

    private void register(Class<?> packetClass, Check... checks) {
        tempPacketMap.computeIfAbsent(packetClass, k -> new ArrayList<>()).addAll(Arrays.asList(checks));
        nameCache.put(packetClass, packetClass.getSimpleName());
        for (Check c : checks) {
            checkNameCache.put(c, c.getClass().getSimpleName());
        }
    }
    public void clearViolations(UUID uuid) {
        violationTracker.remove(uuid);
    }
    public boolean dispatch(Object packet, ProtocolPlayer player) {
        long startTime = System.nanoTime();
        boolean blocked = true;

        try {
            Class<?> packetClass = packet.getClass();
            String packetName = nameCache.computeIfAbsent(packetClass, Class::getSimpleName);

            if (!player.getStateMachine().analyzeCrashCombo(packetClass)) {
                radarLog(player, packetName, "CrashCombo (StateMachine)");
                return false;
            }

            if (!player.getStateMachine().isActionValid(packetClass, player)) {
                radarLog(player, packetName, "InvalidSequence (StateMachine)");
                return false;
            }

            for (Check check : globalChecksArray) {
                long checkStart = System.nanoTime();
                boolean result = runCheck(check, packet, player, packetName);

                plugin.getPerformanceMonitor().recordCheck(check.getClass(), System.nanoTime() - checkStart);

                if (!result) return false;
            }

            Check[] specificChecks = packetMap.get(packetClass);
            if (specificChecks != null) {
                for (Check check : specificChecks) {
                    long checkStart = System.nanoTime();
                    boolean result = runCheck(check, packet, player, packetName);

                    plugin.getPerformanceMonitor().recordCheck(check.getClass(), System.nanoTime() - checkStart);

                    if (!result) return false;
                }
            }

            blocked = false;
            return true;

        } finally {
            long totalDuration = System.nanoTime() - startTime;
            plugin.getMetrics().recordPacket(blocked, totalDuration);
        }
    }

    private boolean runCheck(Check check, Object packet, ProtocolPlayer player, String packetName) {
        String checkName = checkNameCache.get(check);

        if (!safeModeManager.isCheckEnabled(checkName)) return true;

        if (geyserSupport && player.isBedrock() && !check.isBedrockCompatible()) {
            return true;
        }

        try {
            boolean isPassed = check.check(packet, player);

            if (!isPassed) {
                radarLog(player, packetName, checkName);
            }
            return isPassed;

        } catch (Throwable t) {
            safeModeManager.recordException(checkName, t);

            if (!silentFailures) {
                plugin.getLogger().warning("§e[LightGuard] Check Error (" + checkName + ") - Packet passed with tolerance: " + t.getMessage());
            }

            return true;
        }
    }

    private void radarLog(ProtocolPlayer player, String packetName, String blockerName) {
        UUID uuid = player.getPlayer().getUniqueId();

        Map<String, Integer> playerVLs = violationTracker.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        int vl = playerVLs.merge(blockerName, 1, Integer::sum);

        String time = LocalTime.now().format(TIME_FORMATTER);

        String debugMsg = String.format("§8[§e%s§8] §c[LG-RADAR] §f%s§7's §e%s §7packet was blocked by §c%s §8(VL: %d/%d)",
                time, player.getPlayer().getName(), packetName, blockerName, vl, MAX_VIOLATIONS);
        Bukkit.getConsoleSender().sendMessage(debugMsg);

        if (vl == MAX_VIOLATIONS) {
            plugin.getActionQueue().pushKick(player.getPlayer(), "Security Violation: " + blockerName);
        }
    }

    public SafeModeManager getSafeModeManager() {
        return safeModeManager;
    }
}