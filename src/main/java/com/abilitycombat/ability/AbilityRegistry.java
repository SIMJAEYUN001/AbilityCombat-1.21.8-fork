package com.abilitycombat.ability;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AbilityRegistry {

    private final JavaPlugin plugin;
    private final Map<String, AbilityDefinition> definitions = new LinkedHashMap<>();
    private final Random random = new Random();

    public AbilityRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        definitions.clear();
        File file = new File(plugin.getDataFolder(), "abilities.yml");
        if (!file.exists()) {
            plugin.saveResource("abilities.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> list = config.getMapList("abilities");
        for (Map<?, ?> entry : list) {
            String name = toString(entry.get("name"));
            AbilityRank rank = AbilityRank.fromString(toString(entry.get("rank")));
            List<String> summary = toStringList(entry.get("summary"));
            Material icon = toMaterial(entry.get("icon"));
            if (name == null || name.isBlank()) {
                continue;
            }
            AbilityDefinition definition = new AbilityDefinition(name, rank, summary, icon);
            definitions.put(name, definition);
        }
    }

    public Collection<AbilityDefinition> getAll() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public AbilityDefinition getByName(String name) {
        return definitions.get(name);
    }

    public List<AbilityDefinition> getRandomOptions(int count) {
        List<AbilityDefinition> pool = new ArrayList<>(definitions.values());
        Collections.shuffle(pool, random);
        if (pool.size() <= count) {
            return pool;
        }
        return new ArrayList<>(pool.subList(0, count));
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        if (value instanceof String str) {
            return List.of(str);
        }
        return Collections.emptyList();
    }

    private Material toMaterial(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Material.valueOf(String.valueOf(value).toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
