package com.mertout.lightguard.data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ProtocolStateMachine {

    private final AtomicLong lastGhostAction = new AtomicLong(0);

    private final AtomicInteger ghostWindowClicks = new AtomicInteger(0);
    private final AtomicInteger ghostHotbarSwaps = new AtomicInteger(0);
    private final AtomicInteger comboSpamCount = new AtomicInteger(0);

    private final AtomicLong lastTabComplete = new AtomicLong(-1);
    private final AtomicLong lastPayload = new AtomicLong(-1);

    private static final int MAX_WINDOW_CLICKS_PER_SECOND = 20;
    private static final int MAX_HOTBAR_SWAPS_PER_SECOND = 15;
    private static final int MAX_COMBO_SPAM = 15;
    private static final long GHOST_RESET_INTERVAL = 1000L;

    public boolean isActionValid(String packetName, ProtocolPlayer pp) {
        long now = System.currentTimeMillis();

        long lastAction = lastGhostAction.get();
        if (now - lastAction > GHOST_RESET_INTERVAL) {
            if (lastGhostAction.compareAndSet(lastAction, now)) {
                ghostWindowClicks.set(0);
                ghostHotbarSwaps.set(0);
            }
        }

        if (!pp.isInventoryOpen()) {
            if (packetName.equals("PacketPlayInWindowClick")) {
                if (ghostWindowClicks.incrementAndGet() > MAX_WINDOW_CLICKS_PER_SECOND) {
                    return false;
                }
            } else if (packetName.equals("PacketPlayInHeldItemSlot")) {
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

    public boolean analyzeCrashCombo(String packetName) {
        long now = System.currentTimeMillis();

        if (packetName.equals("PacketPlayInTabComplete")) {
            lastTabComplete.set(now);
        } else if (packetName.equals("PacketPlayInCustomPayload")) {
            lastPayload.set(now);
        } else {
            return true;
        }

        long tabTime = lastTabComplete.get();
        long payloadTime = lastPayload.get();

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
        lastGhostAction.set(0);
        comboSpamCount.set(0);
        lastTabComplete.set(-1);
        lastPayload.set(-1);
    }
}