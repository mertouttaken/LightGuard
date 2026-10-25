package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;

public class HybridRateLimiter {

    private final double maxThreshold;
    private final double decayPerNano;
    private final int maxViolations;

    private double currentScore = 0.0;
    private long lastUpdateNanos = System.nanoTime();
    private int violations = 0;

    public HybridRateLimiter(LightGuard plugin) {
        this.maxThreshold = plugin.getConfig().getDouble("checks.flood.max-threshold", 500.0);
        double decayPerSecond = plugin.getConfig().getDouble("checks.flood.decay-per-second", 150.0);
        this.decayPerNano = decayPerSecond / 1_000_000_000.0;
        this.maxViolations = plugin.getConfig().getInt("checks.flood.max-violations", 3);
    }

    public synchronized boolean addAndCheck(double weight) {
        long now = System.nanoTime();
        long elapsedNanos = now - lastUpdateNanos;

        if (elapsedNanos > 0) {
            currentScore = Math.max(0.0, currentScore - (elapsedNanos * decayPerNano));
            lastUpdateNanos = now;
        }

        currentScore += weight;

        if (currentScore > maxThreshold) {
            violations++;
            currentScore = maxThreshold * 0.5;
            return violations >= maxViolations;
        }

        return false;
    }

    public synchronized double getCurrentScore() {
        return currentScore;
    }
}