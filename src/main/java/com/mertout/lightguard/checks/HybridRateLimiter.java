package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class HybridRateLimiter {

    private final double maxThreshold;
    private final double decayPerSecond;
    private final int maxViolations;

    private final AtomicLong threatScoreBits = new AtomicLong(Double.doubleToRawLongBits(0.0));
    private final AtomicInteger violationLevel = new AtomicInteger(0);

    private final AtomicLong lastPacketTime = new AtomicLong(0);
    private final AtomicLong lastViolationDecayTime = new AtomicLong(0);

    private final AtomicLongArray burstTimestamps = new AtomicLongArray(20);
    private final AtomicInteger burstIndex = new AtomicInteger(0);

    public HybridRateLimiter(LightGuard plugin) {
        this.maxThreshold = plugin.getConfig().getDouble("checks.flood.max-threshold", 500.0);
        this.decayPerSecond = plugin.getConfig().getDouble("checks.flood.decay-per-second", 150.0);
        this.maxViolations = plugin.getConfig().getInt("checks.flood.max-violations", 3);
    }

    public boolean addAndCheck(double baseWeight) {
        long now = System.currentTimeMillis();

        lastPacketTime.compareAndSet(0, now);
        lastViolationDecayTime.compareAndSet(0, now);

        long previousTime = lastPacketTime.getAndSet(now);
        long timeDelta = now - previousTime;

        double decayAmount = 0.0;
        if (timeDelta > 0) {
            decayAmount = (timeDelta / 1000.0) * decayPerSecond;
        }

        long lastViolTime = lastViolationDecayTime.get();
        if (now - lastViolTime > 5000) {
            if (lastViolationDecayTime.compareAndSet(lastViolTime, now)) {
                violationLevel.updateAndGet(v -> Math.max(0, v - 1));
            }
        }

        double finalWeight = baseWeight;
        int currentIndex = burstIndex.getAndUpdate(i -> (i + 1) % 20);

        long oldestTimestamp = burstTimestamps.get((currentIndex + 1) % 20);
        if (oldestTimestamp > 0 && (now - oldestTimestamp) < 50) {
            finalWeight *= 4.0;
        }
        burstTimestamps.set(currentIndex, now);

        boolean updated = false;
        double currentScore = 0.0;
        double newScore = 0.0;

        while (!updated) {
            long currentBits = threatScoreBits.get();
            currentScore = Double.longBitsToDouble(currentBits);

            newScore = Math.max(0.0, currentScore - decayAmount) + finalWeight;

            if (newScore > maxThreshold) {
                newScore = maxThreshold * 0.5;
            }

            long newBits = Double.doubleToRawLongBits(newScore);
            updated = threatScoreBits.compareAndSet(currentBits, newBits);
        }

        if (Math.max(0.0, currentScore - decayAmount) + finalWeight > maxThreshold) {
            int currentViolations = violationLevel.incrementAndGet();
            if (currentViolations >= maxViolations) {
                return true;
            }
        }

        return false;
    }

    public double getThreatScore() {
        return Double.longBitsToDouble(threatScoreBits.get());
    }

    public int getViolationLevel() {
        return violationLevel.get();
    }
}