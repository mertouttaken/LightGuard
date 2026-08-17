package com.mertout.lightguard.data;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeepAliveTracker {

    /** The vanilla server disconnects after 30s without a response, so anything older is dead. */
    private static final long STALE_AFTER_MS = 60_000L;

    private final Map<Long, Long> pendingKeepAlives = new ConcurrentHashMap<>();
    private volatile int ping = 0;

    /** False until we have observed an outgoing KeepAlive, i.e. until validation is meaningful. */
    private volatile boolean tracking = false;

    public void onKeepAliveSent(long id) {
        tracking = true;
        pendingKeepAlives.put(id, System.currentTimeMillis());
        evictStaleEntries();
    }

    /**
     * @return false only when we know the id was never sent by this server (spoofed or replayed).
     *         Returns true whenever we lack the information to judge, so a failed outgoing hook
     *         can never time every player out.
     */
    public boolean onKeepAliveReceived(long id) {
        Long sentTime = pendingKeepAlives.remove(id);

        if (sentTime == null) {
            return !tracking;
        }

        this.ping = (int) (System.currentTimeMillis() - sentTime);
        return true;
    }

    public int getPing() {
        return ping;
    }

    private void evictStaleEntries() {
        if (pendingKeepAlives.size() < 32) return;

        long cutoff = System.currentTimeMillis() - STALE_AFTER_MS;
        Iterator<Map.Entry<Long, Long>> it = pendingKeepAlives.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < cutoff) it.remove();
        }
    }

    public void clear() {
        pendingKeepAlives.clear();
    }
}
