package com.mertout.lightguard.data;

public class ProtocolStateMachine {

    private int ghostWindowClicks = 0;

    private long lastTabComplete = 0;
    private long lastPayload = 0;
    private int comboSpamCount = 0;

    public boolean validateWindowClick(ProtocolPlayer player) {
        if (!player.isInventoryOpen()) {
            ghostWindowClicks++;
            if (ghostWindowClicks > 3) return false;
        } else {
            ghostWindowClicks = 0; // Menü açıldığında sayacı sıfırla
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
            comboSpamCount = Math.max(0, comboSpamCount - 1);
        }

        return true;
    }
}