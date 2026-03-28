package com.mertout.lightguard.data;

import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

public class KeepAliveTracker {

    private final Long2LongOpenHashMap pendingKeepAlives = new Long2LongOpenHashMap();
    private int ping = 0;

    public KeepAliveTracker() {
        pendingKeepAlives.defaultReturnValue(-1L);
    }

    public void onKeepAliveSent(long id) {
        pendingKeepAlives.put(id, System.currentTimeMillis());
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

    public void clear() {
        pendingKeepAlives.clear();
    }
}