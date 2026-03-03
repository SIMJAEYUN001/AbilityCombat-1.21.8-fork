package com.abilitycombat.entity;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.utils.collection.QueueOnIterateHashSet;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class CustomEntityManager implements Runnable {

    private static final QueueOnIterateHashSet<CustomEntity> ENTITIES = new QueueOnIterateHashSet<>();
    private static BukkitTask task;

    private CustomEntityManager() {
    }

    public static void start(AbilityCombat plugin) {
        if (task != null || plugin == null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, new CustomEntityManager(), 1L, 1L);
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        ENTITIES.clear();
    }

    static void register(CustomEntity entity) {
        if (entity != null) {
            ENTITIES.add(entity);
        }
    }

    static void unregister(CustomEntity entity) {
        if (entity != null) {
            ENTITIES.remove(entity);
        }
    }

    @Override
    public void run() {
        ENTITIES.forEach(entity -> {
            if (entity == null || !entity.tick()) {
                ENTITIES.remove(entity);
            }
        });
    }
}
