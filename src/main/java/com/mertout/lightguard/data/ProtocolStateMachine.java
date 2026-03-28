package com.mertout.lightguard.data;

public class ProtocolStateMachine {

    private int ghostWindowClicks = 0;
    private int ghostHotbarSwaps = 0;

    private long lastTabComplete = 0;
    private long lastPayload = 0;
    private int comboSpamCount = 0;

    public boolean isActionValid(String packetName, ProtocolPlayer pp) {
        if (pp.isDead() && (packetName.contains("Position") || packetName.contains("Flying"))) {
            return false;
        }

        if (!pp.isInventoryOpen()) {
            if (packetName.equals("PacketPlayInWindowClick")) {
                ghostWindowClicks++;
                if (ghostWindowClicks > 10000) ghostWindowClicks = 10000;

                if (ghostWindowClicks > 5) return false;
            }
            else if (packetName.equals("PacketPlayInHeldItemSlot")) {
                ghostHotbarSwaps++;
                // 🛡️ ELİTE TİER FİX: Int Overflow Koruması
                if (ghostHotbarSwaps > 10000) ghostHotbarSwaps = 10000;

                if (ghostHotbarSwaps > 8) return false;
            }
        } else {
            ghostWindowClicks = 0;
            ghostHotbarSwaps = 0;
        }

        return true;
    }

    public boolean analyzeCrashCombo(String packetName) {
        long now = System.currentTimeMillis();

        if (packetName.equals("PacketPlayInTabComplete")) {
            lastTabComplete = now;
        } else if (packetName.equals("PacketPlayInCustomPayload")) {
            lastPayload = now;
        }

        if (Math.abs(lastTabComplete - lastPayload) < 50) {
            comboSpamCount++;
            if (comboSpamCount > 10) return false;
        } else {
            comboSpamCount = Math.max(0, comboSpamCount - 3);
        }

        return true;
    }
}