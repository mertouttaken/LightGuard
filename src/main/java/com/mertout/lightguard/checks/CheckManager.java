package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.PlayerData;
import com.mertout.lightguard.checks.impl.*;
import com.mertout.lightguard.utils.GeyserUtil;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CheckManager {

    private final LightGuard plugin;
    private final PlayerData data;

    private final Map<Class<?>, List<Check>> packetMap = new HashMap<>();
    private final List<Check> globalChecks = new ArrayList<>();

    private final boolean isBedrock;

    public CheckManager(PlayerData data) {
        this.data = data;
        this.plugin = LightGuard.getInstance();
        this.isBedrock = GeyserUtil.isBedrockPlayer(data.getPlayer());
        loadChecks();
    }

    private void loadChecks() {
        // 1. Global Packets
        globalChecks.add(new FloodCheck(data));
        globalChecks.add(new PacketSizeCheck(data));

        register(PacketPlayInChat.class, new CommandCheck(data), new ChatSecurityCheck(data));
        register(PacketPlayInTabComplete.class, new TabCheck(data));
        register(PacketPlayInWindowClick.class, new ItemExploitCheck(data), new WindowCheck(data), new GameStateCheck(data));
        register(PacketPlayInSetCreativeSlot.class, new ItemExploitCheck(data), new MapExploitCheck(data));
        register(PacketPlayInFlying.class, new PositionCheck(data)); // Flying, Position, Look
        register(PacketPlayInUseItem.class, new BlockPlaceCheck(data));
        register(PacketPlayInCustomPayload.class, new PayloadCheck(data));
        register(PacketPlayInBEdit.class, new BookExploitCheck(data));
        register(PacketPlayInUpdateSign.class, new SignCheck(data));
        register(PacketPlayInSteerVehicle.class, new VehicleCheck(data));
        register(PacketPlayInVehicleMove.class, new VehicleCheck(data));
        register(PacketPlayInUseEntity.class, new BadPacketCheck(data), new EntityCheck(data));
        register(PacketPlayInResourcePackStatus.class, new ResourcePackCheck(data));
        register(PacketPlayInAutoRecipe.class, new RecipeCheck(data));
        register(PacketPlayInRecipeDisplayed.class, new RecipeCheck(data));
        register(PacketPlayInItemName.class, new AnvilCheck(data));
    }

    private void register(Class<?> packetClass, Check... checks) {
        packetMap.computeIfAbsent(packetClass, k -> new ArrayList<>()).addAll(List.of(checks));
    }

    public boolean handlePacket(Object packet) {
        boolean sentinelEnabled = plugin.getConfig().getBoolean("settings.sentinel.enabled", true);
        List<String> criticalChecks = plugin.getConfig().getStringList("settings.sentinel.critical-checks");

        // 1. Offline/Dead Packet Fix
        if (plugin.getConfig().getBoolean("mechanics.block-dead-packets")) {
            if (data.getPlayer().isDead() || !data.getPlayer().isOnline()) {
                String name = packet.getClass().getSimpleName();
                if (name.contains("Window") || name.contains("UseItem") || name.contains("Interact")) {
                    return false;
                }
            }
        }

        if (!runChecks(globalChecks, packet, sentinelEnabled, criticalChecks)) return false;

        Class<?> clazz = packet.getClass();
        List<Check> specificChecks = packetMap.get(clazz);

        if (specificChecks == null && packet instanceof PacketPlayInFlying) {
            specificChecks = packetMap.get(PacketPlayInFlying.class);
        }

        if (specificChecks != null) {
            if (!runChecks(specificChecks, packet, sentinelEnabled, criticalChecks)) return false;
        }

        return true;
    }

    private boolean runChecks(List<Check> checks, Object packet, boolean sentinel, List<String> critical) {
        boolean geyserSupport = plugin.getConfig().getBoolean("settings.sentinel.geyser-support", true);

        for (Check check : checks) {
            // Geyser Bypass
            if (isBedrock && geyserSupport) {
                String checkClassName = check.getClass().getSimpleName();
                if (checkClassName.equals("VehicleCheck") || checkClassName.equals("PositionCheck")) {
                    continue;
                }
            }

            try {
                if (!check.check(packet)) return false;
            } catch (Throwable t) {
                // Sentinel Error Handling
                String checkName = check.getName();
                plugin.getLogger().warning("[Sentinel] Error in '" + checkName + "': " + t.getMessage());
                if (plugin.getConfig().getBoolean("settings.debug", false)) t.printStackTrace();

                if (sentinel && critical.contains(checkName)) {
                    return false; // Fail-closed
                }
            }
        }
        return true;
    }
}