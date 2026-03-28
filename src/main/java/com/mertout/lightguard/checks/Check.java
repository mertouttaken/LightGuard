package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import com.mertout.lightguard.data.ProtocolPlayer;

public abstract class Check {

    protected final LightGuard plugin;

    public Check(LightGuard plugin) {
        this.plugin = plugin;
    }

    public abstract boolean check(Object packet, ProtocolPlayer player);

    public boolean isBedrockCompatible() {
        return true;
    }
}