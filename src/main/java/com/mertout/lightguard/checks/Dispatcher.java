package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.impl.*;
import com.mertout.lightguard.data.ProtocolPlayer;
import com.mertout.lightguard.logger.PacketLoggerManager;
import net.minecraft.server.v1_16_R3.*;

import java.util.*;

public class Dispatcher {

    private final LightGuard plugin;
    private final Map<Class<?>, List<Check>> packetMap = new HashMap<>();
    private final List<Check> globalChecks = new ArrayList<>();
    private final SafeModeManager safeModeManager;
    private final ViolationRadar violationRadar;

    public Dispatcher(LightGuard plugin, ViolationRadar violationRadar) {
        this.plugin = plugin;
        this.safeModeManager = new SafeModeManager(plugin);
        this.violationRadar = violationRadar;
        loadChecks();
    }

    private void loadChecks() {
        globalChecks.add(new PacketSizeCheck(plugin));
        globalChecks.add(new ProtocolExploitCheck(plugin));

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
    }

    private void register(Class<?> packetClass, Check... checks) {
        packetMap.computeIfAbsent(packetClass, k -> new ArrayList<>()).addAll(Arrays.asList(checks));
    }

    public boolean dispatch(Object packet, ProtocolPlayer player) {
        long startTime = System.nanoTime();
        boolean blocked = true;

        try {
            String packetName = packet.getClass().getSimpleName();

            if (!player.getStateMachine().analyzeCrashCombo(packetName)) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Exploit Combo Pattern (Crash Attempt)");
                return false;
            }

            if (!player.getStateMachine().isActionValid(packetName, player)) {
                plugin.getActionQueue().pushKick(player.getPlayer(), "Invalid Sequence / State Action");
                return false;
            }

            for (Check check : globalChecks) {
                if (!timedCheck(check, packet, player)) return false;
            }

            List<Check> specificChecks = packetMap.get(packet.getClass());
            if (specificChecks != null) {
                for (Check check : specificChecks) {
                    if (!timedCheck(check, packet, player)) return false;
                }
            }

            blocked = false;
            return true;

        } finally {
            long totalDuration = System.nanoTime() - startTime;
            plugin.getMetrics().recordPacket(blocked, totalDuration);

            PacketLoggerManager logger = plugin.getPacketLoggerManager();
            if (logger.getConfig().isEnabled()) {
                logger.processPacket(player.getPlayer().getName(), packet, totalDuration);
            }
        }
    }

    private boolean timedCheck(Check check, Object packet, ProtocolPlayer player) {
        long checkStart = System.nanoTime();
        boolean result = runCheck(check, packet, player);
        plugin.getPerformanceMonitor().recordCheck(check.getClass(), System.nanoTime() - checkStart);
        return result;
    }

    private boolean runCheck(Check check, Object packet, ProtocolPlayer player) {
        String checkName = check.getClass().getSimpleName();

        if (!safeModeManager.isCheckEnabled(checkName, player.getUuid())) return true;

        boolean geyserSupport = plugin.getConfig().getBoolean("settings.sentinel.geyser-support", true);
        if (geyserSupport && player.isBedrock() && !check.isBedrockCompatible()) {
            return true;
        }

        try {
            boolean isPassed = check.check(packet, player);

            if (!isPassed) {
                violationRadar.recordViolation(player, checkName);
            }
            return isPassed;

        } catch (Throwable t) {
            safeModeManager.recordException(checkName, player.getUuid(), t);

            boolean silent = plugin.getConfig().getBoolean("settings.sentinel.silent-failures", false);
            if (!silent) {
                plugin.getLogger().warning("[LightGuard] " + checkName + " threw " + t.getClass().getSimpleName()
                        + " while validating a " + packet.getClass().getSimpleName() + " packet.");
            }

            boolean sentinelEnabled = plugin.getConfig().getBoolean("settings.sentinel.enabled", true);
            if (sentinelEnabled && safeModeManager.isCritical(checkName)) {
                violationRadar.recordViolation(player, checkName);
                return false;
            }

            return true;
        }
    }

    public void clearViolations(UUID uuid) {
        violationRadar.clearViolations(uuid);
        safeModeManager.clear(uuid);
    }

    public SafeModeManager getSafeModeManager() {
        return safeModeManager;
    }
}