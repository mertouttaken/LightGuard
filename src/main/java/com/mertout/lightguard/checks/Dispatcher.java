package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.impl.*;
import com.mertout.lightguard.data.ProtocolPlayer;
import com.mertout.lightguard.logger.PacketLoggerManager;

import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.*;

import java.util.*;
import java.util.function.Supplier;

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

    /**
     * Disabled checks are never registered, rather than being constructed and then skipped inside
     * {@link Check#check}. A disabled check therefore costs nothing per packet. The Dispatcher is
     * rebuilt by {@code LightGuard#reloadCheckDispatcher()}, so toggles apply on /lg reload.
     */
    private void loadChecks() {
        addGlobal("packet-size", () -> new PacketSizeCheck(plugin));
        addGlobal("protocol-exploit", () -> new ProtocolExploitCheck(plugin));

        Check commandCheck = ifEnabled("command", () -> new CommandCheck(plugin));
        // Commands arrive unsigned, or signed when any argument is signable (1.20.5+). Registering
        // only the unsigned form let every signed command skip the blacklist and the Log4Shell filter.
        register(ServerboundChatCommandPacket.class, commandCheck);
        register(ServerboundChatCommandSignedPacket.class, commandCheck);

        // Chat itself has not carried commands since 1.19, so CommandCheck is not registered here.
        register(ServerboundChatPacket.class, ifEnabled("chat-unicode", () -> new ChatSecurityCheck(plugin)));

        register(ServerboundCommandSuggestionPacket.class, ifEnabled("tab", () -> new TabCheck(plugin)));

        Check itemExploit = ifEnabled("item-exploit", () -> new ItemExploitCheck(plugin));
        Check windowCheck = ifEnabled("window", () -> new WindowCheck(plugin));
        register(ServerboundContainerClickPacket.class, itemExploit, windowCheck);
        register(ServerboundPickItemFromBlockPacket.class, windowCheck);
        register(ServerboundPickItemFromEntityPacket.class, windowCheck);
        register(ServerboundSetCreativeModeSlotPacket.class, itemExploit,
                ifEnabled("map-exploit", () -> new MapExploitCheck(plugin)),
                ifEnabled("creative", () -> new CreativeCheck(plugin)));

        register(ServerboundRenameItemPacket.class, ifEnabled("anvil", () -> new AnvilCheck(plugin)));
        register(ServerboundSetCarriedItemPacket.class, ifEnabled("held-item", () -> new HeldItemSlotCheck(plugin)));
        register(ServerboundEditBookPacket.class, ifEnabled("book", () -> new BookExploitCheck(plugin)));

        Check posCheck = ifEnabled("position", () -> new PositionCheck(plugin));
        register(ServerboundMovePlayerPacket.class, posCheck);
        register(ServerboundMovePlayerPacket.Pos.class, posCheck);
        register(ServerboundMovePlayerPacket.Rot.class, posCheck);
        register(ServerboundMovePlayerPacket.PosRot.class, posCheck);
        register(ServerboundMovePlayerPacket.StatusOnly.class, posCheck);

        register(ServerboundKeepAlivePacket.class, ifEnabled("keep-alive", () -> new KeepAliveCheck(plugin)));

        // ServerboundPlayerInputPacket is deliberately unregistered: since 1.21.2 it carries all
        // player input every tick, not just vehicle steering, and holds nothing that can be invalid.
        register(ServerboundMoveVehiclePacket.class, ifEnabled("vehicle", () -> new VehicleCheck(plugin)));

        Check blockPlace = ifEnabled("block-place", () -> new BlockPlaceCheck(plugin));
        register(ServerboundUseItemOnPacket.class, blockPlace);
        register(ServerboundUseItemPacket.class, blockPlace);

        register(ServerboundSignUpdatePacket.class, ifEnabled("sign", () -> new SignCheck(plugin)));
        register(ServerboundPlayerActionPacket.class, ifEnabled("no-com", () -> new NoComCheck(plugin)));

        register(ServerboundInteractPacket.class,
                ifEnabled("bad-packets", () -> new BadPacketCheck(plugin)),
                ifEnabled("entity", () -> new EntityCheck(plugin)));

        register(ServerboundCustomPayloadPacket.class, ifEnabled("payload", () -> new PayloadCheck(plugin)));
        register(ServerboundClientInformationPacket.class, ifEnabled("client-settings", () -> new SettingsCheck(plugin)));

        Check recipeCheck = ifEnabled("recipe", () -> new RecipeCheck(plugin));
        register(ServerboundPlaceRecipePacket.class, recipeCheck);
        register(ServerboundRecipeBookSeenRecipePacket.class, recipeCheck);

        register(ServerboundPlayerAbilitiesPacket.class, ifEnabled("abilities", () -> new AbilitiesCheck(plugin)));
        register(ServerboundClientCommandPacket.class, ifEnabled("client-action", () -> new ClientActionCheck(plugin)));
    }

    /** @return the constructed check, or null when {@code checks.<key>.enabled} is false. */
    private <T extends Check> T ifEnabled(String key, Supplier<T> factory) {
        if (!plugin.getConfig().getBoolean("checks." + key + ".enabled", true)) {
            return null;
        }
        return factory.get();
    }

    private void addGlobal(String key, Supplier<? extends Check> factory) {
        Check check = ifEnabled(key, factory);
        if (check != null) globalChecks.add(check);
    }

    private void register(Class<?> packetClass, Check... checks) {
        for (Check check : checks) {
            if (check == null) continue;
            packetMap.computeIfAbsent(packetClass, k -> new ArrayList<>()).add(check);
        }
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
            if (logger != null && logger.getConfig().isEnabled()) {
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
