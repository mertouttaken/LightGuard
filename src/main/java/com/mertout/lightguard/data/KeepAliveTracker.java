package com.mertout.lightguard.data;


import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

public class KeepAliveTracker {

    // FastUtil: Primitive (İlkel) yapıdır. Asla RAM'de "Long" objesi üretmez!
    private final Long2LongOpenHashMap pendingKeepAlives = new Long2LongOpenHashMap();
    private int ping = 0;

    public KeepAliveTracker() {
        // Eğer map'te bulunmayan bir ID sorgulanırsa -1 döndürsün (Obje null check'inden kurtulduk)
        pendingKeepAlives.defaultReturnValue(-1L);
    }

    /**
     * Sunucudan oyuncuya KeepAlive paketi giderken (PacketInjector.write)
     */
    public void onKeepAliveSent(long id) {
        pendingKeepAlives.put(id, System.currentTimeMillis());

        // 🚨 Disabler (Hile) Koruması:
        // Eğer adam gelen KeepAlive'lara cevap vermeyip biriktiriyorsa (Hoarding), Disabler kullanıyordur.
        if (pendingKeepAlives.size() > 10) {
            // Çok fazla bekleyen KeepAlive var, Disabler pattern tespit edildi!
        }
    }

    /**
     * Oyuncudan sunucuya cevap geldiğinde (PacketPlayInKeepAlive)
     * @return ping süresi (ms). Eğer -1 dönerse sahte/spoofed pakettir.
     */
    public long onKeepAliveReceived(long id) {
        long sentTime = pendingKeepAlives.remove(id); // Hem oku hem sil (O(1))

        if (sentTime == -1L) {
            return -1L; // 🚨 Sunucunun yollamadığı sahte bir ID geldi! (Spoof Attempt)
        }

        this.ping = (int) (System.currentTimeMillis() - sentTime);
        return this.ping;
    }

    public int getPing() {
        return ping;
    }
}