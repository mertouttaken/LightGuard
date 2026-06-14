package com.mertout.lightguard.data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ProtocolStateMachine {

    private final AtomicInteger ghostWindowClicks = new AtomicInteger(0);
    private final AtomicInteger ghostHotbarSwaps = new AtomicInteger(0);
    private final AtomicLong lastGhostAction = new AtomicLong(0);

    private final AtomicLong lastTabComplete = new AtomicLong(-1);
    private final AtomicLong lastPayload = new AtomicLong(-1);
    private final AtomicInteger comboSpamCount = new AtomicInteger(0);

    public boolean isActionValid(String packetName, ProtocolPlayer pp) {
        long now = System.currentTimeMillis();

        if (now - lastGhostAction.get() > 1000) {
            ghostWindowClicks.set(0);
            ghostHotbarSwaps.set(0);
        }

        if (!pp.isInventoryOpen()) {
            if (packetName.equals("PacketPlayInWindowClick")) {
                lastGhostAction.set(now);
                int clicks = ghostWindowClicks.incrementAndGet();

                if (clicks > 100) ghostWindowClicks.set(100);

                if (clicks > 5) return false;
            }
            else if (packetName.equals("PacketPlayInHeldItemSlot")) {
                lastGhostAction.set(now);
                int swaps = ghostHotbarSwaps.incrementAndGet();

                if (swaps > 100) ghostHotbarSwaps.set(100);

                if (swaps > 8) return false;
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
        }

        long tabTime = lastTabComplete.get();
        long payloadTime = lastPayload.get();

        if (tabTime == -1 || payloadTime == -1) {
            return true;
        }

        if (Math.abs(tabTime - payloadTime) < 50) {
            int spam = comboSpamCount.incrementAndGet();
            if (spam > 10) return false;
        } else {
            comboSpamCount.updateAndGet(current -> Math.max(0, current - 3));
        }

        return true;
    }
}