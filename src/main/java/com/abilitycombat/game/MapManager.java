package com.abilitycombat.game;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * maps.yml 파일을 관리하고 맵 데이터를 제공하는 매니저
 */
public class MapManager {

    private final File mapsFile;
    private final Logger logger;
    private final Map<String, MapData> maps = new HashMap<>();
    private final Random random = new Random();

    public MapManager(File dataFolder, Logger logger) {
        this.mapsFile = new File(dataFolder, "maps.yml");
        this.logger = logger;
    }

    /**
     * maps.yml에서 맵 데이터 로드
     */
    public void load() {
        maps.clear();
        if (!mapsFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(mapsFile);
        ConfigurationSection mapsSection = config.getConfigurationSection("maps");
        if (mapsSection == null) {
            return;
        }
        for (String id : mapsSection.getKeys(false)) {
            ConfigurationSection mapSection = mapsSection.getConfigurationSection(id);
            if (mapSection == null) {
                continue;
            }
            String name = mapSection.getString("name", id);
            String worldName = mapSection.getString("world", "");
            double x = mapSection.getDouble("x", 0);
            double y = mapSection.getDouble("y", 64);
            double z = mapSection.getDouble("z", 0);
            float yaw = (float) mapSection.getDouble("yaw", 0);
            float pitch = (float) mapSection.getDouble("pitch", 0);
            maps.put(id, new MapData(id, name, worldName, x, y, z, yaw, pitch));
        }
        logger.info("Loaded " + maps.size() + " maps from maps.yml");
    }

    /**
     * maps.yml에 맵 데이터 저장
     */
    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (MapData map : maps.values()) {
            String path = "maps." + map.getId();
            config.set(path + ".name", map.getName());
            config.set(path + ".world", map.getWorldName());
            config.set(path + ".x", (int) map.getX()); // 정수형으로 저장
            config.set(path + ".y", map.getY());
            config.set(path + ".z", (int) map.getZ()); // 정수형으로 저장
            config.set(path + ".yaw", map.getYaw());
            config.set(path + ".pitch", map.getPitch());
        }
        try {
            config.save(mapsFile);
        } catch (IOException e) {
            logger.warning("Failed to save maps.yml: " + e.getMessage());
        }
    }

    /**
     * 새 맵 추가
     */
    public MapData addMap(String name, Location location) {
        String id = generateId();
        MapData map = new MapData(id, name, location);
        maps.put(id, map);
        save();
        return map;
    }

    /**
     * 맵 삭제
     */
    public boolean removeMap(String id) {
        if (maps.remove(id) != null) {
            save();
            return true;
        }
        return false;
    }

    /**
     * 맵 이름 변경
     */
    public boolean renameMap(String id, String newName) {
        MapData map = maps.get(id);
        if (map != null) {
            map.setName(newName);
            save();
            return true;
        }
        return false;
    }

    /**
     * ID로 맵 조회
     */
    public MapData getMap(String id) {
        return maps.get(id);
    }

    /**
     * 전체 맵 목록
     */
    public Collection<MapData> getAllMaps() {
        return Collections.unmodifiableCollection(maps.values());
    }

    /**
     * 맵 개수
     */
    public int getMapCount() {
        return maps.size();
    }

    /**
     * 랜덤 맵 선택
     */
    public MapData getRandomMap() {
        if (maps.isEmpty()) {
            return null;
        }
        List<MapData> list = new ArrayList<>(maps.values());
        return list.get(random.nextInt(list.size()));
    }

    /**
     * 맵이 존재하는지 확인
     */
    public boolean hasMap(String id) {
        return maps.containsKey(id);
    }

    /**
     * 고유 ID 생성
     */
    private String generateId() {
        String id;
        do {
            id = "map_" + UUID.randomUUID().toString().substring(0, 8);
        } while (maps.containsKey(id));
        return id;
    }
}
