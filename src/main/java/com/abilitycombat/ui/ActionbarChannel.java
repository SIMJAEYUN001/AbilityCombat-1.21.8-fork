package com.abilitycombat.ui;

import com.abilitycombat.AbilityCombat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ActionbarChannel implements Runnable {

    private static final long PERIOD_TICKS = 20L;

    private final AbilityCombat plugin;
    private final Map<UUID, Map<String, Entry>> entries = new HashMap<>();
    private final Map<UUID, Boolean> hadMessage = new HashMap<>();
    private final Map<UUID, Component> lastSent = new HashMap<>();
    private long sequence = 0L;
    private int taskId = -1;

    public ActionbarChannel(AbilityCombat plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (taskId != -1) {
            return;
        }
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this, 0L, PERIOD_TICKS);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        entries.clear();
        hadMessage.clear();
        lastSent.clear();
    }

    public void update(Player player, String key, int priority, Component message) {
        update(player, key, priority, message, 0L);
    }

    public void updateForTicks(Player player, String key, int priority, Component message, int ticks) {
        update(player, key, priority, message, ticks <= 0 ? 0L : System.currentTimeMillis() + ticks * 50L);
    }

    public void clear(Player player, String key) {
        if (player == null || key == null) {
            return;
        }
        Map<String, Entry> map = entries.get(player.getUniqueId());
        if (map != null) {
            map.remove(key);
            if (map.isEmpty()) {
                entries.remove(player.getUniqueId());
            }
        }
    }

    public void clearAll(Player player) {
        if (player == null) {
            return;
        }
        entries.remove(player.getUniqueId());
        hadMessage.remove(player.getUniqueId());
        lastSent.remove(player.getUniqueId());
    }

    private void update(Player player, String key, int priority, Component message, long expireAt) {
        if (player == null || key == null || message == null) {
            return;
        }
        Map<String, Entry> map = entries.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        Entry entry = map.get(key);
        if (entry == null) {
            entry = new Entry();
            map.put(key, entry);
        }
        entry.priority = priority;
        entry.message = message;
        entry.expireAt = expireAt;
        entry.sequence = ++sequence;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Map<String, Entry> map = entries.get(uuid);
            if (map == null || map.isEmpty()) {
                clearIfNeeded(player);
                continue;
            }
            Entry selected = selectEntry(map);
            if (selected == null) {
                clearIfNeeded(player);
                continue;
            }
            player.sendActionBar(selected.message);
            lastSent.put(uuid, selected.message);
            hadMessage.put(uuid, true);
        }
        cleanupOffline();
    }

    private void clearIfNeeded(Player player) {
        UUID uuid = player.getUniqueId();
        if (hadMessage.getOrDefault(uuid, false) || lastSent.containsKey(uuid)) {
            player.sendActionBar(Component.empty());
            hadMessage.put(uuid, false);
            lastSent.remove(uuid);
        }
    }

    private Entry selectEntry(Map<String, Entry> map) {
        long now = System.currentTimeMillis();
        Entry best = null;
        Iterator<Map.Entry<String, Entry>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.expireAt > 0 && entry.expireAt <= now) {
                iterator.remove();
                continue;
            }
            if (best == null) {
                best = entry;
                continue;
            }
            if (entry.priority > best.priority) {
                best = entry;
                continue;
            }
            if (entry.priority == best.priority && entry.sequence > best.sequence) {
                best = entry;
            }
        }
        return best;
    }

    private void cleanupOffline() {
        entries.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        hadMessage.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        lastSent.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private static final class Entry {
        private int priority;
        private Component message;
        private long expireAt;
        private long sequence;
    }
}
