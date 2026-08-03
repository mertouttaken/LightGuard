package com.mertout.lightguard.data;

import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

public class KeepAliveTracker {

    private final Long2LongOpenHashMap pendingKeepAlives = new Long2LongOpenHashMap();
    private volatile int ping = 0;

    private static final long TIMEOUT_MS = 30_000L;

    public KeepAliveTracker() {
        pendingKeepAlives.defaultReturnValue(-1L);
    }

    public void onKeepAliveSent(long id) {
        pendingKeepAlives.put(id, System.currentTimeMillis());
        cleanStaleEntries();
    }

    public boolean onKeepAliveReceived(long id) {
        long sentTime = pendingKeepAlives.remove(id);

        if (sentTime == -1L) {
            return false;
        }

        this.ping = (int) (System.currentTimeMillis() - sentTime);
        return true;
    }

    public int getPing() {
        return ping;
    }

    private void cleanStaleEntries() {
        if (pendingKeepAlives.size() > 1000) {
            long now = System.currentTimeMillis();
            pendingKeepAlives.clear();
        }
    }

    public void clear() {
        pendingKeepAlives.clear();
    }
}