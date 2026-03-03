package com.abilitycombat.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * 맵 정보를 담는 데이터 클래스
 */
public class MapData {

    private final String id;
    private String name;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    public MapData(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        if (location != null && location.getWorld() != null) {
            this.worldName = location.getWorld().getName();
            this.x = location.getBlockX(); // 정수형으로 저장
            this.y = location.getY();
            this.z = location.getBlockZ(); // 정수형으로 저장
            this.yaw = location.getYaw();
            this.pitch = location.getPitch();
        }
    }

    public MapData(String id, String name, String worldName, double x, double y, double z, float yaw, float pitch) {
        this.id = id;
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorldName() {
        return worldName;
    }

    public Location getLocation() {
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    /**
     * 스냅샷 디렉토리 이름으로 사용할 안전한 ID 반환
     */
    public String getSafeId() {
        return id.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
