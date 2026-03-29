package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;


public class HybridRateLimiter {

    private final double maxThreshold;
    private final double decayPerSecond;
    private final int maxViolations;

    private double threatScore = 0.0;
    private int violationLevel = 0;

    private long lastPacketTime = 0;
    private long lastViolationDecayTime = 0;

    private final long[] burstTimestamps = new long[20];
    private int burstIndex = 0;

    public HybridRateLimiter(LightGuard plugin) {
        this.maxThreshold = plugin.getConfig().getDouble("checks.flood.max-threshold", 500.0);
        this.decayPerSecond = plugin.getConfig().getDouble("checks.flood.decay-per-second", 150.0);
        this.maxViolations = plugin.getConfig().getInt("checks.flood.max-violations", 3);
    }

    public synchronized boolean addAndCheck(double baseWeight) {
        long now = System.currentTimeMillis();

        if (lastPacketTime == 0) {
            lastPacketTime = now;
            lastViolationDecayTime = now;
        }

        long timeDelta = now - lastPacketTime;
        if (timeDelta > 0) {
            double decayAmount = (timeDelta / 1000.0) * decayPerSecond;
            this.threatScore = Math.max(0.0, this.threatScore - decayAmount);
        }
        this.lastPacketTime = now;

        long violationTimeDelta = now - lastViolationDecayTime;
        if (violationTimeDelta > 5000) {
            if (this.violationLevel > 0) {
                this.violationLevel--;
            }
            this.lastViolationDecayTime = now;
        }

        double finalWeight = baseWeight;
        long oldestTimestamp = burstTimestamps[(burstIndex + 1) % 20];

        if (oldestTimestamp > 0 && (now - oldestTimestamp) < 50) {
            finalWeight *= 4.0;
        }

        burstTimestamps[burstIndex] = now;
        burstIndex = (burstIndex + 1) % 20;

        this.threatScore += finalWeight;

        if (this.threatScore > maxThreshold) {
            this.violationLevel++;

            this.threatScore = maxThreshold * 0.5;

            if (this.violationLevel >= maxViolations) {
                return true;
            }
        }

        return false;
    }

    public double getThreatScore() {
        return threatScore;
    }

    public int getViolationLevel() {
        return violationLevel;
    }
}