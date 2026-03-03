package com.abilitycombat.utils;

import com.abilitycombat.ability.AbilityTickManager;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public final class NearbyEntityCache {

    private int lastTick = -1000;
    private Location lastCenter;
    private double lastRadius = -1.0;
    private Predicate<LivingEntity> lastPredicate;
    private List<LivingEntity> cached = Collections.emptyList();

    public List<LivingEntity> getNearby(Location center, double radius, Predicate<LivingEntity> predicate, int cacheTicks) {
        if (center == null || center.getWorld() == null) {
            return Collections.emptyList();
        }
        int tick = AbilityTickManager.getGlobalTick();
        if (isCacheValid(center, radius, predicate, tick, cacheTicks)) {
            return cached;
        }
        cached = List.copyOf(LocationUtil.getNearbyLivingEntities(center, radius, predicate));
        lastCenter = center.clone();
        lastRadius = radius;
        lastPredicate = predicate;
        lastTick = tick;
        return cached;
    }

    public void clear() {
        cached = Collections.emptyList();
        lastCenter = null;
        lastRadius = -1.0;
        lastPredicate = null;
        lastTick = -1000;
    }

    private boolean isCacheValid(Location center, double radius, Predicate<LivingEntity> predicate, int tick,
            int cacheTicks) {
        if (lastCenter == null || lastCenter.getWorld() == null) {
            return false;
        }
        if (cacheTicks <= 0) {
            return false;
        }
        if (tick - lastTick >= cacheTicks) {
            return false;
        }
        if (lastRadius != radius) {
            return false;
        }
        if (lastPredicate != predicate) {
            return false;
        }
        if (!lastCenter.getWorld().equals(center.getWorld())) {
            return false;
        }
        return lastCenter.distanceSquared(center) <= 1.0;
    }
}
