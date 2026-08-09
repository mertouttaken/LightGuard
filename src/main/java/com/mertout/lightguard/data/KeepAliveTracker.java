package com.mertout.lightguard.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeepAliveTracker {

    private final Map<Long, Long> pendingKeepAlives = new ConcurrentHashMap<>();
    private volatile int ping = 0;

    public void onKeepAliveSent(long id) {
        pendingKeepAlives.put(id, System.currentTimeMillis());
        cleanStaleEntries();
    }

    public boolean onKeepAliveReceived(long id) {
        Long sentTime = pendingKeepAlives.remove(id);

        if (sentTime == null) {
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
            pendingKeepAlives.clear();
        }
    }

    public void clear() {
        pendingKeepAlives.clear();
    }
}