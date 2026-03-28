package com.mertout.lightguard.data;

public class ProtocolStateMachine {

    // Hayalet tıklama sayacı (Menü açık değilken tıklama)
    private int ghostWindowClicks = 0;

    // Combo spam (Crash Pattern) tespiti için zaman damgaları
    private long lastTabComplete = 0;
    private long lastPayload = 0;
    private int comboSpamCount = 0;

    /**
     * Oyuncunun envanter açık olmadan tıklama yapıp yapmadığını kontrol eder.
     * Desync ve hayalet tıklama açıklarına karşı korur.
     */
    public boolean validateWindowClick(ProtocolPlayer player) {
        if (!player.isInventoryOpen()) {
            ghostWindowClicks++;
            // 3 ardışık hayalet tıklama = Kesin Exploit/Desync
            if (ghostWindowClicks > 3) return false;
        } else {
            ghostWindowClicks = 0; // Menü açıldığında sayacı sıfırla
        }
        return true;
    }

    /**
     * Gelişmiş Crash Client'ların sıklıkla kullandığı "TabComplete + CustomPayload"
     * kombinasyon saldırısını (Combo Attack) analiz eder.
     */
    public boolean analyzeCrashCombo(String packetName) {
        long now = System.currentTimeMillis();

        if (packetName.equals("PacketPlayInTabComplete")) {
            lastTabComplete = now;
        } else if (packetName.equals("PacketPlayInCustomPayload")) {
            lastPayload = now;
        }

        // TabComplete ve CustomPayload 50ms içinde art arda geliyorsa şüpheli
        if (Math.abs(lastTabComplete - lastPayload) < 50) {
            comboSpamCount++;
            // Bu kombinasyon 10 kere hızlıca tekrarlanırsa kesin Crash Attempt'tir
            if (comboSpamCount > 10) return false;
        } else {
            // Kombinasyon yoksa puanı zamanla düşür (Soğuma)
            comboSpamCount = Math.max(0, comboSpamCount - 1);
        }

        return true;
    }
}