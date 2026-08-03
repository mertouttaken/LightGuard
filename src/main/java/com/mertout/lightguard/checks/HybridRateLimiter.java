package com.mertout.lightguard.checks;

import com.mertout.lightguard.LightGuard;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class HybridRateLimiter {

    private static double MAX_THRESHOLD = 500.0;
    private static double DECAY_PER_MS = 0.15;
    private static int MAX_VIOLATIONS = 3;
    private static volatile boolean configLoaded = false;

    private static final int BURST_SIZE = 16;
    private static final int BURST_MASK = 15;

    private volatile long threatScoreBits = Double.doubleToRawLongBits(0.0);
    private volatile int violationLevel = 0;

    private volatile long lastPacketTime = 0;
    private volatile long lastViolationDecayTime = 0;
    private volatile int burstIndex = 0;

    private final long[] burstTimestamps = new long[BURST_SIZE];

    private static final AtomicLongFieldUpdater<HybridRateLimiter> THREAT_SCORE_UPDATER = AtomicLongFieldUpdater.newUpdater(HybridRateLimiter.class, "threatScoreBits");
    private static final AtomicIntegerFieldUpdater<HybridRateLimiter> VIOLATION_UPDATER = AtomicIntegerFieldUpdater.newUpdater(HybridRateLimiter.class, "violationLevel");
    private static final AtomicLongFieldUpdater<HybridRateLimiter> LAST_PACKET_TIME_UPDATER = AtomicLongFieldUpdater.newUpdater(HybridRateLimiter.class, "lastPacketTime");
    private static final AtomicLongFieldUpdater<HybridRateLimiter> LAST_VIOLATION_DECAY_UPDATER = AtomicLongFieldUpdater.newUpdater(HybridRateLimiter.class, "lastViolationDecayTime");
    private static final AtomicIntegerFieldUpdater<HybridRateLimiter> BURST_INDEX_UPDATER = AtomicIntegerFieldUpdater.newUpdater(HybridRateLimiter.class, "burstIndex");

    public HybridRateLimiter(LightGuard plugin) {
        if (!configLoaded) {
            synchronized (HybridRateLimiter.class) {
                if (!configLoaded) {
                    MAX_THRESHOLD = plugin.getConfig().getDouble("checks.flood.max-threshold", 500.0);
                    DECAY_PER_MS = plugin.getConfig().getDouble("checks.flood.decay-per-second", 150.0) / 1000.0;
                    MAX_VIOLATIONS = plugin.getConfig().getInt("checks.flood.max-violations", 3);
                    configLoaded = true;
                }
            }
        }
    }

    public boolean addAndCheck(double baseWeight) {
        long now = System.currentTimeMillis();

        LAST_PACKET_TIME_UPDATER.compareAndSet(this, 0, now);
        LAST_VIOLATION_DECAY_UPDATER.compareAndSet(this, 0, now);

        long previousTime = LAST_PACKET_TIME_UPDATER.getAndSet(this, now);
        long timeDelta = now - previousTime;

        double decayAmount = 0.0;
        if (timeDelta > 0) {
            decayAmount = timeDelta * DECAY_PER_MS;
        }

        long lastViolTime = lastViolationDecayTime;
        if (now - lastViolTime > 5000) {
            if (LAST_VIOLATION_DECAY_UPDATER.compareAndSet(this, lastViolTime, now)) {
                int currentViol;
                int nextViol;
                do {
                    currentViol = violationLevel;
                    if (currentViol == 0) break;
                    nextViol = currentViol - 1;
                } while (!VIOLATION_UPDATER.compareAndSet(this, currentViol, nextViol));
            }
        }

        double finalWeight = baseWeight;

        int currentIndex;
        int nextIndex;
        do {
            currentIndex = burstIndex;
            nextIndex = (currentIndex + 1) & BURST_MASK;
        } while (!BURST_INDEX_UPDATER.compareAndSet(this, currentIndex, nextIndex));

        long oldestTimestamp = burstTimestamps[(currentIndex + 1) & BURST_MASK];
        if (oldestTimestamp > 0 && (now - oldestTimestamp) < 50) {
            finalWeight *= 4.0;
        }
        burstTimestamps[currentIndex] = now;

        while (true) {
            long currentBits = threatScoreBits;
            double currentScore = Double.longBitsToDouble(currentBits);

            double newScore = Math.max(0.0, currentScore - decayAmount) + finalWeight;

            boolean thresholdExceeded = newScore > MAX_THRESHOLD;
            if (thresholdExceeded) {
                newScore = MAX_THRESHOLD * 0.5;
            }

            long newBits = Double.doubleToRawLongBits(newScore);

            if (THREAT_SCORE_UPDATER.compareAndSet(this, currentBits, newBits)) {
                if (thresholdExceeded) {
                    if (VIOLATION_UPDATER.incrementAndGet(this) >= MAX_VIOLATIONS) {
                        return true;
                    }
                }
                break;
            }
        }

        return false;
    }

    public double getThreatScore() {
        return Double.longBitsToDouble(threatScoreBits);
    }

    public int getViolationLevel() {
        return violationLevel;
    }
}