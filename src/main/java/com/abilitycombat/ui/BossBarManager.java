package com.abilitycombat.ui;

import com.abilitycombat.AbilityCombat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BossBarManager implements Listener {

    private final Map<UUID, Map<String, Entry>> entries = new HashMap<>();
    private final Map<UUID, String> activeKeys = new HashMap<>();
    private long sequence = 0L;

    public BossBarManager(AbilityCombat plugin) {
    }

    public void update(Player player, String key, int priority, Component title, float progress, BossBar.Color color,
            BossBar.Overlay overlay) {
        if (player == null || key == null || title == null || color == null || overlay == null) {
            return;
        }
        Map<String, Entry> map = entries.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        Entry entry = map.get(key);
        if (entry == null) {
            entry = new Entry(BossBar.bossBar(title, clamp(progress), color, overlay));
            entry.title = title;
            entry.progress = clamp(progress);
            entry.color = color;
            entry.overlay = overlay;
            map.put(key, entry);
        } else {
            float clamped = clamp(progress);
            if (!title.equals(entry.title)) {
                entry.bar.name(title);
                entry.title = title;
            }
            if (entry.progress != clamped) {
                entry.bar.progress(clamped);
                entry.progress = clamped;
            }
            if (entry.color != color) {
                entry.bar.color(color);
                entry.color = color;
            }
            if (entry.overlay != overlay) {
                entry.bar.overlay(overlay);
                entry.overlay = overlay;
            }
        }
        entry.priority = priority;
        entry.sequence = ++sequence;
        refresh(player);
    }

    public void clear(Player player, String key) {
        if (player == null || key == null) {
            return;
        }
        Map<String, Entry> map = entries.get(player.getUniqueId());
        if (map == null) {
            return;
        }
        Entry removed = map.remove(key);
        if (removed != null) {
            player.hideBossBar(removed.bar);
        }
        if (map.isEmpty()) {
            entries.remove(player.getUniqueId());
            activeKeys.remove(player.getUniqueId());
        } else {
            refresh(player);
        }
    }

    public void clearAll(Player player) {
        if (player == null) {
            return;
        }
        Map<String, Entry> map = entries.remove(player.getUniqueId());
        activeKeys.remove(player.getUniqueId());
        if (map != null) {
            for (Entry entry : map.values()) {
                player.hideBossBar(entry.bar);
            }
        }
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearAll(player);
        }
        entries.clear();
        activeKeys.clear();
    }

    private void refresh(Player player) {
        Map<String, Entry> map = entries.get(player.getUniqueId());
        if (map == null || map.isEmpty()) {
            clearActive(player);
            return;
        }
        String bestKey = null;
        Entry best = null;
        for (Map.Entry<String, Entry> entry : map.entrySet()) {
            Entry value = entry.getValue();
            if (best == null) {
                best = value;
                bestKey = entry.getKey();
                continue;
            }
            if (value.priority > best.priority) {
                best = value;
                bestKey = entry.getKey();
                continue;
            }
            if (value.priority == best.priority && value.sequence > best.sequence) {
                best = value;
                bestKey = entry.getKey();
            }
        }
        String activeKey = activeKeys.get(player.getUniqueId());
        if (bestKey == null || best == null) {
            clearActive(player);
            return;
        }
        if (bestKey.equals(activeKey)) {
            return;
        }
        if (activeKey != null) {
            Entry active = map.get(activeKey);
            if (active != null) {
                player.hideBossBar(active.bar);
            }
        }
        player.showBossBar(best.bar);
        activeKeys.put(player.getUniqueId(), bestKey);
    }

    private void clearActive(Player player) {
        String activeKey = activeKeys.remove(player.getUniqueId());
        if (activeKey == null) {
            return;
        }
        Map<String, Entry> map = entries.get(player.getUniqueId());
        if (map == null) {
            return;
        }
        Entry active = map.get(activeKey);
        if (active != null) {
            player.hideBossBar(active.bar);
        }
    }

    private float clamp(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearAll(event.getPlayer());
    }

    private static final class Entry {
        private final BossBar bar;
        private Component title;
        private float progress;
        private BossBar.Color color;
        private BossBar.Overlay overlay;
        private int priority;
        private long sequence;

        private Entry(BossBar bar) {
            this.bar = bar;
        }
    }
}
