package com.abilitycombat.combat;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Allows ability-origin sweep particles to pass packet filtering.
 */
public final class SweepEffectAllowance {

    private static final long ALLOWANCE_WINDOW_NANOS = 150_000_000L;
    private static final ConcurrentLinkedQueue<Long> PARTICLE_ALLOWANCES = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<Long> SOUND_ALLOWANCES = new ConcurrentLinkedQueue<>();

    private SweepEffectAllowance() {
    }

    public static void markAbilitySweepParticle() {
        PARTICLE_ALLOWANCES.add(System.nanoTime());
    }

    public static boolean consumeAbilitySweepParticleAllowance() {
        long now = System.nanoTime();
        cleanupExpired(now);
        Long allowed = PARTICLE_ALLOWANCES.poll();
        if (allowed == null) {
            return false;
        }
        return (now - allowed) <= ALLOWANCE_WINDOW_NANOS;
    }

    public static void markAbilitySweepSound() {
        SOUND_ALLOWANCES.add(System.nanoTime());
    }

    public static boolean consumeAbilitySweepSoundAllowance() {
        long now = System.nanoTime();
        cleanupExpired(now);
        Long allowed = SOUND_ALLOWANCES.poll();
        if (allowed == null) {
            return false;
        }
        return (now - allowed) <= ALLOWANCE_WINDOW_NANOS;
    }

    private static void cleanupExpired(long now) {
        cleanupQueue(PARTICLE_ALLOWANCES, now);
        cleanupQueue(SOUND_ALLOWANCES, now);
    }

    private static void cleanupQueue(ConcurrentLinkedQueue<Long> queue, long now) {
        while (true) {
            Long peek = queue.peek();
            if (peek == null) {
                return;
            }
            if ((now - peek) <= ALLOWANCE_WINDOW_NANOS) {
                return;
            }
            queue.poll();
        }
    }
}
