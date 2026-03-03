package com.abilitycombat.ability;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.utils.collection.QueueOnIterateHashSet;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class AbilityTickManager implements Runnable {

    public static final int TICK_INTERVAL = 1;
    private static final QueueOnIterateHashSet<Tickable> TICKABLES = new QueueOnIterateHashSet<>();
    private static BukkitTask task;
    private static int globalTick = 0;

    private AbilityTickManager() {
    }

    public static void start(AbilityCombat plugin) {
        if (task != null || plugin == null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, new AbilityTickManager(), 1L, TICK_INTERVAL);
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        TICKABLES.clear();
        globalTick = 0;
    }

    public static void register(Tickable tickable) {
        if (tickable != null) {
            TICKABLES.add(tickable);
        }
    }

    public static void unregister(Tickable tickable) {
        if (tickable != null) {
            TICKABLES.remove(tickable);
        }
    }

    public static int getGlobalTick() {
        return globalTick;
    }

    @Override
    public void run() {
        globalTick += TICK_INTERVAL;
        TICKABLES.forEach(tickable -> {
            if (tickable == null) {
                TICKABLES.remove(null);
                return;
            }
            try {
                tickable.onTick(globalTick);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public interface Tickable {
        void onTick(int tick);
    }
}
