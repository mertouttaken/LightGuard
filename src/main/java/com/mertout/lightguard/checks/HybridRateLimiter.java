package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;

public class HybridRateLimiter {

    private volatile double threatScore = 0.0;
    private volatile long lastDecayTime;

    private final long[] packetTimestamps = new long[20];
    private int index = 0;

    private final double maxThreshold;
    private final double decayPerSecond;

    public HybridRateLimiter(LightGuard plugin) {
        this.maxThreshold = plugin.getConfig().getDouble("checks.flood.max-threshold", 500.0);
        this.decayPerSecond = plugin.getConfig().getDouble("checks.flood.decay-per-second", 150.0);
    }

    public boolean addAndCheck(double baseWeight) {
        long now = System.currentTimeMillis();

        long timePassed = now - lastDecayTime;
        if (timePassed > 1000) {
            double decayAmount = (timePassed / 1000.0) * decayPerSecond;
            this.threatScore = Math.max(0.0, this.threatScore - decayAmount);
            this.lastDecayTime = now;
        }

        double finalWeight = baseWeight;

        long oldestTimestamp = packetTimestamps[(index + 1) % 20];

        if (oldestTimestamp > 0 && (now - oldestTimestamp) < 50) {
            finalWeight *= 4.0;
        }

        packetTimestamps[index] = now;
        index = (index + 1) % 20;

        this.threatScore += finalWeight;

        return this.threatScore > maxThreshold;
    }

    public double getThreatScore() {
        return threatScore;
    }
}