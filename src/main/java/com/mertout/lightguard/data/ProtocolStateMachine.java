package com.mertout.lightguard.data;

import net.minecraft.server.v1_16_R3.PacketPlayInCustomPayload;
import net.minecraft.server.v1_16_R3.PacketPlayInHeldItemSlot;
import net.minecraft.server.v1_16_R3.PacketPlayInTabComplete;
import net.minecraft.server.v1_16_R3.PacketPlayInWindowClick;

import java.util.concurrent.atomic.AtomicInteger;

public class ProtocolStateMachine {

    private volatile long lastGhostAction = 0;

    private final AtomicInteger ghostWindowClicks = new AtomicInteger(0);
    private final AtomicInteger ghostHotbarSwaps = new AtomicInteger(0);
    private final AtomicInteger comboSpamCount = new AtomicInteger(0);

    private volatile long lastTabComplete = -1;
    private volatile long lastPayload = -1;

    private static final int MAX_WINDOW_CLICKS_PER_SECOND = 20;
    private static final int MAX_HOTBAR_SWAPS_PER_SECOND = 15;
    private static final int MAX_COMBO_SPAM = 15;
    private static final long GHOST_RESET_INTERVAL = 1000L;

    public boolean isActionValid(Class<?> packetClass, ProtocolPlayer pp) {
        long now = System.currentTimeMillis();

        if (now - lastGhostAction > GHOST_RESET_INTERVAL) {
            ghostWindowClicks.set(0);
            ghostHotbarSwaps.set(0);
            lastGhostAction = now;
        }

        if (!pp.isInventoryOpen()) {
            if (packetClass == PacketPlayInWindowClick.class) {
                if (ghostWindowClicks.incrementAndGet() > MAX_WINDOW_CLICKS_PER_SECOND) {
                    return false;
                }
            } else if (packetClass == PacketPlayInHeldItemSlot.class) {
                if (ghostHotbarSwaps.incrementAndGet() > MAX_HOTBAR_SWAPS_PER_SECOND) {
                    return false;
                }
            }
        } else {
            ghostWindowClicks.set(0);
            ghostHotbarSwaps.set(0);
        }

        return true;
    }

    public boolean analyzeCrashCombo(Class<?> packetClass) {
        long now = System.currentTimeMillis();

        if (packetClass == PacketPlayInTabComplete.class) {
            lastTabComplete = now;
        } else if (packetClass == PacketPlayInCustomPayload.class) {
            lastPayload = now;
        } else {
            return true;
        }

        long tabTime = lastTabComplete;
        long payloadTime = lastPayload;

        if (tabTime == -1 || payloadTime == -1) {
            return true;
        }

        if (Math.abs(tabTime - payloadTime) < 50) {
            if (comboSpamCount.incrementAndGet() > MAX_COMBO_SPAM) {
                return false;
            }
        } else {
            int current;
            int next;
            do {
                current = comboSpamCount.get();
                if (current == 0) break;
                next = Math.max(0, current - 3);
            } while (!comboSpamCount.compareAndSet(current, next));
        }

        return true;
    }

    public void reset() {
        ghostWindowClicks.set(0);
        ghostHotbarSwaps.set(0);
        lastGhostAction = 0;
        comboSpamCount.set(0);
    }
}