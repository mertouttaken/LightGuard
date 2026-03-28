package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.impl.*;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.*;

import java.util.*;

public class Dispatcher {

    private final LightGuard plugin;
    private final Map<Class<?>, List<Check>> packetMap = new HashMap<>();
    private final List<Check> globalChecks = new ArrayList<>();
    private final SafeModeManager safeModeManager;

    public Dispatcher(LightGuard plugin) {
        this.plugin = plugin;
        this.safeModeManager = new SafeModeManager(plugin);
        loadChecks();
    }

    private void loadChecks() {
        globalChecks.add(new PacketSizeCheck(plugin));
        globalChecks.add(new ProtocolExploitCheck(plugin));

        register(PacketPlayInChat.class, new CommandCheck(plugin), new ChatSecurityCheck(plugin));
        register(PacketPlayInTabComplete.class, new TabCheck(plugin));

        register(PacketPlayInWindowClick.class, new ItemExploitCheck(plugin), new WindowCheck(plugin), new GameStateCheck(plugin));
        register(PacketPlayInSetCreativeSlot.class, new ItemExploitCheck(plugin), new MapExploitCheck(plugin));
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
        register(PacketPlayInSpectate.class, new SpectateCheck(plugin));
    }

    private void register(Class<?> packetClass, Check... checks) {
        packetMap.computeIfAbsent(packetClass, k -> new ArrayList<>()).addAll(Arrays.asList(checks));
    }

    public boolean dispatch(Object packet, ProtocolPlayer player) {
        long startTime = System.nanoTime();
        boolean blocked = true; // Varsayılan olarak bloklandı kabul edip sonunda düzelteceğiz

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
                long checkStart = System.nanoTime();
                boolean result = runCheck(check, packet, player);
                plugin.getPerformanceMonitor().recordCheck(check.getClass().getSimpleName(), System.nanoTime() - checkStart);
                if (!result) return false;
            }

            List<Check> specificChecks = packetMap.get(packet.getClass());
            if (specificChecks != null) {
                for (Check check : specificChecks) {
                    long checkStart = System.nanoTime();
                    boolean result = runCheck(check, packet, player);
                    plugin.getPerformanceMonitor().recordCheck(check.getClass().getSimpleName(), System.nanoTime() - checkStart);
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

    private boolean runCheck(Check check, Object packet, ProtocolPlayer player) {
        String checkName = check.getClass().getSimpleName();

        if (!safeModeManager.isCheckEnabled(checkName)) return true;

        boolean geyserSupport = plugin.getConfig().getBoolean("settings.sentinel.geyser-support", true);
        if (geyserSupport && player.isBedrock() && !check.isBedrockCompatible()) {
            return true;
        }

        try {
            return check.check(packet, player);
        } catch (Throwable t) {
            safeModeManager.recordException(checkName, t);

            boolean silent = plugin.getConfig().getBoolean("settings.sentinel.silent-failures", false);
            if (!silent) {
                plugin.getLogger().warning("§c[LightGuard] Error in " + checkName + ": " + t.getMessage());
            }

            boolean sentinelEnabled = plugin.getConfig().getBoolean("settings.sentinel.enabled", true);
            if (sentinelEnabled && safeModeManager.isCritical(checkName)) {
                return false;
            }

            return true;
        }
    }

    public SafeModeManager getSafeModeManager() {
        return safeModeManager;
    }
}