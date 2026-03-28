package com.mertout.lightguard.checks;

public class HybridRateLimiter {

    private volatile double threatScore = 0.0;
    private volatile long lastDecayTime;

    private final long[] packetTimestamps = new long[20];
    private int index = 0;

    private static final double MAX_THRESHOLD = 500.0;
    private static final double DECAY_PER_SECOND = 150.0;

    public HybridRateLimiter() {
        this.lastDecayTime = System.currentTimeMillis();
    }

    public boolean addAndCheck(double baseWeight) {
        long now = System.currentTimeMillis();

        long timePassed = now - lastDecayTime;
        if (timePassed > 1000) {
            double decayAmount = (timePassed / 1000.0) * DECAY_PER_SECOND;
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

        return this.threatScore > MAX_THRESHOLD;
    }

    public double getThreatScore() {
        return threatScore;
    }
}