package com.mertout.lightguard.checks.impl;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.checks.Check;
import com.mertout.lightguard.data.ProtocolPlayer;
import net.minecraft.server.v1_16_R3.PacketPlayInWindowClick;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class WindowCheck extends Check {

    private static final VarHandle SLOT_FIELD;
    private static final VarHandle BUTTON_FIELD;
    private static final VarHandle CLICK_TYPE_FIELD;

    static {
        VarHandle slot = null, button = null, clickType = null;
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(PacketPlayInWindowClick.class, MethodHandles.lookup());
            int intFieldCount = 0;

            for (Field field : PacketPlayInWindowClick.class.getDeclaredFields()) {
                field.setAccessible(true);

                if (field.getType() == int.class) {
                    if (intFieldCount == 1) slot = lookup.unreflectVarHandle(field);
                    else if (intFieldCount == 2) button = lookup.unreflectVarHandle(field);
                    intFieldCount++;
                } else if (Enum.class.isAssignableFrom(field.getType()) && clickType == null) {
                    clickType = lookup.unreflectVarHandle(field);
                }
            }
        } catch (Exception ignored) {}

        SLOT_FIELD = slot;
        BUTTON_FIELD = button;
        CLICK_TYPE_FIELD = clickType;
    }

    public WindowCheck(LightGuard plugin) {
        super(plugin);
    }

    @Override
    public boolean check(Object packet, ProtocolPlayer player) {
        if (!(packet instanceof PacketPlayInWindowClick)) return true;

        try {
            if (SLOT_FIELD == null || CLICK_TYPE_FIELD == null || BUTTON_FIELD == null) return true;

            int slot = (int) SLOT_FIELD.get(packet);
            int button = (int) BUTTON_FIELD.get(packet);

            Enum<?> type = (Enum<?>) CLICK_TYPE_FIELD.get(packet);

            if (button < 0 || button > 100 || slot > 127 || (slot < -1 && slot != -999)) {
                return false;
            }

            if (type != null) {
                String typeName = type.name();

                if (typeName.equals("QUICK_MOVE")) {
                    if (player.incrementAndGetQuickMoveCount(System.currentTimeMillis()) > 50) {
                        return false;
                    }
                } else if (typeName.equals("CLONE")) {
                    if (!player.isCreativeOrSpectator()) {
                        return false;
                    }
                }
            }

        } catch (Throwable t) {
            return true;
        }

        return true;
    }
}