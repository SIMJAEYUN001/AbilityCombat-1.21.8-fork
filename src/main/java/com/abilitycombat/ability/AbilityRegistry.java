package com.abilitycombat.ability;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AbilityRegistry {

    private final JavaPlugin plugin;
    private final Map<String, AbilityDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Long> pickCounts = new LinkedHashMap<>();
    private long randomSelectionSeed = 0;
    private long totalPicks = 0L;

    public AbilityRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        definitions.clear();
        File file = new File(plugin.getDataFolder(), "abilities.yml");
        ensureAbilitiesFileVersion(file);
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
        mergeRegisteredDescriptors();
        loadPickRates();
    }

    private void mergeRegisteredDescriptors() {
        for (AbilityDescriptor descriptor : AbilityFactory.getRegisteredDescriptors()) {
            if (descriptor == null || descriptor.name() == null || descriptor.name().isBlank()) {
                continue;
            }
            definitions.putIfAbsent(descriptor.name(), new AbilityDefinition(
                    descriptor.name(),
                    AbilityRank.A,
                    descriptor.summarize(),
                    descriptor.icon()));
        }
    }

    private void loadPickRates() {
        pickCounts.clear();
        File file = new File(plugin.getDataFolder(), "pickrate.yml");
        if (!file.exists()) {
            plugin.saveResource("pickrate.yml", false);
            totalPicks = 0L;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        totalPicks = Math.max(0L, config.getLong("total-picks", 0L));
        if (config.isConfigurationSection("abilities")) {
            for (String key : config.getConfigurationSection("abilities").getKeys(false)) {
                long count = Math.max(0L, config.getLong("abilities." + key, 0L));
                if (count > 0L) {
                    pickCounts.put(key, count);
                }
            }
        }
    }

    public void recordPick(AbilityDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            return;
        }
        pickCounts.merge(definition.getName(), 1L, Long::sum);
        totalPicks++;
        savePickRates();
    }

    public long getPickCount(String abilityName) {
        if (abilityName == null || abilityName.isBlank()) {
            return 0L;
        }
        return pickCounts.getOrDefault(abilityName, 0L);
    }

    public long getPickCount(AbilityDefinition definition) {
        return definition == null ? 0L : getPickCount(definition.getName());
    }

    public double getPickRatePercent(String abilityName) {
        if (totalPicks <= 0L) {
            return 0.0D;
        }
        return (getPickCount(abilityName) * 100.0D) / totalPicks;
    }

    public double getPickRatePercent(AbilityDefinition definition) {
        return definition == null ? 0.0D : getPickRatePercent(definition.getName());
    }

    public long getTotalPicks() {
        return totalPicks;
    }

    private void savePickRates() {
        File file = new File(plugin.getDataFolder(), "pickrate.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("version", plugin.getPluginMeta().getVersion());
        config.set("total-picks", totalPicks);
        for (Map.Entry<String, Long> entry : pickCounts.entrySet()) {
            config.set("abilities." + entry.getKey(), entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save pickrate.yml: " + exception.getMessage());
        }
    }

    private void ensureAbilitiesFileVersion(File file) {
        if (!file.exists()) {
            plugin.saveResource("abilities.yml", false);
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String fileVersion = config.getString("version");
        String pluginVersion = plugin.getPluginMeta().getVersion();

        if (fileVersion == null || fileVersion.isBlank()) {
            plugin.getLogger().info("abilities.yml version not found. Replacing with bundled file.");
            plugin.saveResource("abilities.yml", true);
            return;
        }

        if (compareVersion(fileVersion, pluginVersion) < 0) {
            plugin.getLogger().info("abilities.yml version " + fileVersion
                    + " is older than plugin version " + pluginVersion
                    + ". Replacing abilities.yml.");
            plugin.saveResource("abilities.yml", true);
        }
    }

    private int compareVersion(String left, String right) {
        List<Long> leftParts = parseVersionNumbers(left);
        List<Long> rightParts = parseVersionNumbers(right);
        int length = Math.max(leftParts.size(), rightParts.size());
        for (int i = 0; i < length; i++) {
            long leftValue = i < leftParts.size() ? leftParts.get(i) : 0L;
            long rightValue = i < rightParts.size() ? rightParts.get(i) : 0L;
            if (leftValue != rightValue) {
                return Long.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private List<Long> parseVersionNumbers(String version) {
        List<Long> parts = new ArrayList<>();
        if (version == null || version.isBlank()) {
            return parts;
        }

        long value = -1;
        for (int i = 0; i < version.length(); i++) {
            char ch = version.charAt(i);
            if (ch >= '0' && ch <= '9') {
                value = Math.max(0, value) * 10 + (ch - '0');
                continue;
            }
            if (value >= 0) {
                parts.add(value);
                value = -1;
            }
        }
        if (value >= 0) {
            parts.add(value);
        }
        return parts;
    }

    public Collection<AbilityDefinition> getAll() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public AbilityDefinition getByName(String name) {
        return definitions.get(name);
    }

    public List<AbilityDefinition> getRandomOptions(int count) {
        List<AbilityDefinition> pool = new ArrayList<>(definitions.values());
        sortBySelectionHash(pool);
        if (pool.size() <= count) {
            return pool;
        }
        return new ArrayList<>(pool.subList(0, count));
    }

    private void sortBySelectionHash(List<AbilityDefinition> pool) {
        long seed = nextSelectionSeed();
        pool.sort(Comparator.comparingLong(ability -> hash(ability.getName(), seed)));
    }

    private long nextSelectionSeed() {
        randomSelectionSeed += 0x9e3779b97f4a7c15L;
        return randomSelectionSeed ^ System.nanoTime();
    }

    private long hash(String input, long seed) {
        long x = seed;
        if (input != null) {
            for (int i = 0; i < input.length(); i++) {
                x ^= (0x9e3779b97f4a7c15L * (input.charAt(i) + 1L));
                x = Long.rotateLeft(x, 17) + 0x9e3779b97f4a7c15L;
            }
        }
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return x;
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
