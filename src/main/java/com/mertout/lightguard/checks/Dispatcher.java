package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.impl.*;
import com.mertout.lightguard.data.ProtocolPlayer;
import com.mertout.lightguard.logger.PacketLoggerManager;

import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.*;

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

        register(ServerboundChatPacket.class, new CommandCheck(plugin), new ChatSecurityCheck(plugin));
        register(ServerboundChatCommandPacket.class, new CommandCheck(plugin));

        register(ServerboundCommandSuggestionPacket.class, new TabCheck(plugin));

        register(ServerboundContainerClickPacket.class, new ItemExploitCheck(plugin), new WindowCheck(plugin));
        register(ServerboundSetCreativeModeSlotPacket.class, new ItemExploitCheck(plugin), new MapExploitCheck(plugin), new CreativeCheck(plugin));
        register(ServerboundRenameItemPacket.class, new AnvilCheck(plugin));
        register(ServerboundSetCarriedItemPacket.class, new HeldItemSlotCheck(plugin));
        register(ServerboundEditBookPacket.class, new BookExploitCheck(plugin));

        PositionCheck posCheck = new PositionCheck(plugin);
        register(ServerboundMovePlayerPacket.class, posCheck);
        register(ServerboundMovePlayerPacket.Pos.class, posCheck);
        register(ServerboundMovePlayerPacket.Rot.class, posCheck);
        register(ServerboundMovePlayerPacket.PosRot.class, posCheck);
        register(ServerboundMovePlayerPacket.StatusOnly.class, posCheck);

        register(ServerboundKeepAlivePacket.class, new KeepAliveCheck(plugin));

        VehicleCheck vehicleCheck = new VehicleCheck(plugin);
        register(ServerboundPlayerInputPacket.class, vehicleCheck);
        register(ServerboundMoveVehiclePacket.class, vehicleCheck);

        register(ServerboundUseItemOnPacket.class, new BlockPlaceCheck(plugin));
        register(ServerboundUseItemPacket.class, new BlockPlaceCheck(plugin));

        register(ServerboundSignUpdatePacket.class, new SignCheck(plugin));
        register(ServerboundPlayerActionPacket.class, new NoComCheck(plugin));

        register(ServerboundInteractPacket.class, new BadPacketCheck(plugin), new EntityCheck(plugin));

        register(ServerboundCustomPayloadPacket.class, new PayloadCheck(plugin), new BrandCheck(plugin));
        register(ServerboundClientInformationPacket.class, new SettingsCheck(plugin));
        register(ServerboundResourcePackPacket.class, new ResourcePackCheck(plugin));
        register(ServerboundPlaceRecipePacket.class, new RecipeCheck(plugin));
        register(ServerboundRecipeBookSeenRecipePacket.class, new RecipeCheck(plugin));
        register(ServerboundPlayerAbilitiesPacket.class, new AbilitiesCheck(plugin));
        register(ServerboundClientCommandPacket.class, new ClientActionCheck(plugin));
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