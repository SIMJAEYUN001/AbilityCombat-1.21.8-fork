package com.abilitycombat.game;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class RegionSnapshot {

    private static final int FORMAT_VERSION = 1;
    private static final String DEFAULT_MAP_ID = "default";
    private static final BlockData FALLBACK_BLOCK_DATA = Bukkit.createBlockData(Material.AIR);

    private final Path baseSnapshotDir;
    private Path snapshotDir;
    private Path metaFile;
    private final Logger logger;
    private final Set<Long> chunkKeys = new HashSet<>();

    private String mapId;
    private World world;
    private String worldName;
    private int centerX;
    private int centerZ;
    private int radius;
    private boolean captured;

    /**
     * 기본 맵 ID로 생성 (하위 호환)
     */
    public RegionSnapshot(Path dataFolder, Logger logger) {
        this(dataFolder, logger, DEFAULT_MAP_ID);
    }

    /**
     * 특정 맵 ID로 생성
     */
    public RegionSnapshot(Path dataFolder, Logger logger, String mapId) {
        this.baseSnapshotDir = dataFolder.resolve("map-snapshots");
        this.logger = logger;
        setMapId(mapId);
    }

    /**
     * 맵 ID 변경 및 디렉토리 갱신
     */
    public void setMapId(String mapId) {
        this.mapId = mapId != null && !mapId.isEmpty() ? mapId : DEFAULT_MAP_ID;
        this.snapshotDir = baseSnapshotDir.resolve(this.mapId);
        this.metaFile = snapshotDir.resolve("snapshot.yml");
        // 새 맵으로 전환 시 기존 데이터 클리어
        chunkKeys.clear();
        world = null;
        worldName = null;
        centerX = 0;
        centerZ = 0;
        radius = 0;
        captured = false;
    }

    public String getMapId() {
        return mapId;
    }

    public void prepare(Location center, int radius) {
        clearFiles();
        if (center == null || center.getWorld() == null) {
            return;
        }
        this.world = center.getWorld();
        this.worldName = world.getName();
        this.centerX = center.getBlockX();
        this.centerZ = center.getBlockZ();
        this.radius = Math.max(0, radius);
        ensureSnapshotDir();
        chunkKeys.clear();
        captured = false;
    }

    public void storeSnapshot(int chunkX, int chunkZ, ChunkSnapshot snapshot) {
        if (snapshot == null || world == null) {
            return;
        }
        ensureSnapshotDir();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int height = maxY - minY;
        int size = height * 16 * 16;
        short[] indices = new short[size];
        Map<String, Integer> paletteIndex = new HashMap<>();
        List<String> palette = new ArrayList<>();
        int cursor = 0;
        for (int y = minY; y < maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockData data = snapshot.getBlockData(x, y, z);
                    String key = data.getAsString();
                    Integer idx = paletteIndex.get(key);
                    if (idx == null) {
                        idx = palette.size();
                        paletteIndex.put(key, idx);
                        palette.add(key);
                    }
                    indices[cursor++] = (short) (int) idx;
                }
            }
        }
        if (palette.size() > 0xFFFF) {
            logger.warning("Palette too large, skipping chunk " + chunkX + "," + chunkZ);
            return;
        }
        Path file = getChunkFile(chunkX, chunkZ);
        try {
            writeSnapshotFile(file, minY, maxY, palette, indices);
            chunkKeys.add(toKey(chunkX, chunkZ));
        } catch (IOException ex) {
            logger.warning("Failed to store snapshot chunk " + chunkX + "," + chunkZ + ": " + ex.getMessage());
        }
    }

    public void finishCapture() {
        captured = true;
        saveMetadata();
    }

    public boolean hasCaptured() {
        return captured;
    }

    public World getWorld() {
        if (world == null && worldName != null && !worldName.isEmpty()) {
            world = Bukkit.getWorld(worldName);
        }
        return world;
    }

    public int getRadius() {
        return radius;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public SnapshotData loadSnapshot(long key) {
        Path file = getChunkFile(keyToChunkX(key), keyToChunkZ(key));
        if (!Files.exists(file)) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                logger.warning("Unsupported snapshot version: " + version);
                return null;
            }
            int minY = in.readInt();
            int maxY = in.readInt();
            int paletteSize = in.readInt();
            BlockData[] palette = new BlockData[paletteSize];
            List<String> paletteStrings = new ArrayList<>(paletteSize);
            boolean sanitized = false;
            for (int i = 0; i < paletteSize; i++) {
                String data = in.readUTF();
                String normalizedData = normalizeBlockDataString(data);
                paletteStrings.add(normalizedData);
                BlockData blockData = parseBlockData(normalizedData);
                if (blockData == null) {
                    sanitized = true;
                    palette[i] = FALLBACK_BLOCK_DATA;
                    logger.warning("Unsupported snapshot block data '" + data + "' in map '" + mapId
                            + "', replaced with air.");
                } else {
                    palette[i] = blockData;
                    if (!normalizedData.equals(data)) {
                        sanitized = true;
                    }
                }
            }
            int height = maxY - minY;
            short[] indices = new short[height * 16 * 16];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = (short) in.readUnsignedShort();
            }
            if (sanitized) {
                rewriteSanitizedChunk(file, minY, maxY, palette, indices);
            }
            return new SnapshotData(minY, maxY, palette, indices);
        } catch (IOException ex) {
            logger.warning("Failed to load snapshot chunk " + key + ": " + ex.getMessage());
            return null;
        }
    }

    public Set<Long> snapshotKeys() {
        return new HashSet<>(chunkKeys);
    }

    public void clear() {
        chunkKeys.clear();
        world = null;
        worldName = null;
        centerX = 0;
        centerZ = 0;
        radius = 0;
        captured = false;
    }

    public void loadFromDisk() {
        if (!Files.exists(metaFile)) {
            return;
        }
        YamlConfiguration meta = YamlConfiguration.loadConfiguration(metaFile.toFile());
        worldName = meta.getString("world", "");
        centerX = meta.getInt("center-x", 0);
        centerZ = meta.getInt("center-z", 0);
        radius = meta.getInt("radius", 0);
        world = getWorld();
        captured = world != null;
        chunkKeys.clear();
        if (Files.exists(snapshotDir)) {
            try {
                Files.list(snapshotDir)
                        .filter(path -> path.getFileName().toString().startsWith("chunk_"))
                        .forEach(path -> {
                            Long key = parseChunkKey(path.getFileName().toString());
                            if (key != null) {
                                chunkKeys.add(key);
                            }
                        });
            } catch (IOException ex) {
                logger.warning("Failed to read snapshot files: " + ex.getMessage());
            }
        }
    }

    public boolean isBlockWithinRadius(int x, int z) {
        int dx = x - centerX;
        int dz = z - centerZ;
        return (dx * dx + dz * dz) <= (radius * radius);
    }

    public boolean isChunkWithinRadius(int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;

        int dx;
        if (centerX < minX) {
            dx = minX - centerX;
        } else if (centerX > maxX) {
            dx = centerX - maxX;
        } else {
            dx = 0;
        }

        int dz;
        if (centerZ < minZ) {
            dz = minZ - centerZ;
        } else if (centerZ > maxZ) {
            dz = centerZ - maxZ;
        } else {
            dz = 0;
        }

        return (dx * dx + dz * dz) <= (radius * radius);
    }

    private void saveMetadata() {
        ensureSnapshotDir();
        YamlConfiguration meta = new YamlConfiguration();
        meta.set("world", worldName != null ? worldName : "");
        meta.set("center-x", centerX);
        meta.set("center-z", centerZ);
        meta.set("radius", radius);
        try {
            meta.save(metaFile.toFile());
        } catch (IOException ex) {
            logger.warning("Failed to save snapshot metadata: " + ex.getMessage());
        }
    }

    private void ensureSnapshotDir() {
        try {
            Files.createDirectories(snapshotDir);
        } catch (IOException ex) {
            logger.warning("Failed to create snapshot directory: " + ex.getMessage());
        }
    }

    private void clearFiles() {
        if (!Files.exists(snapshotDir)) {
            clear();
            return;
        }
        try {
            Files.walk(snapshotDir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            logger.warning("Failed to delete snapshot file: " + ex.getMessage());
                        }
                    });
        } catch (IOException ex) {
            logger.warning("Failed to clear snapshot directory: " + ex.getMessage());
        }
        clear();
    }

    private Path getChunkFile(int chunkX, int chunkZ) {
        return snapshotDir.resolve("chunk_" + chunkX + "_" + chunkZ + ".bin");
    }

    private void rewriteSanitizedChunk(Path file, int minY, int maxY, BlockData[] palette, short[] indices) {
        List<String> sanitizedPalette = new ArrayList<>(palette.length);
        for (BlockData blockData : palette) {
            sanitizedPalette.add(blockData.getAsString());
        }
        try {
            writeSnapshotFile(file, minY, maxY, sanitizedPalette, indices);
        } catch (IOException ex) {
            logger.warning("Failed to rewrite sanitized snapshot chunk " + file.getFileName() + ": "
                    + ex.getMessage());
        }
    }

    private void writeSnapshotFile(Path file, int minY, int maxY, List<String> palette, short[] indices) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(file))))) {
            out.writeInt(FORMAT_VERSION);
            out.writeInt(minY);
            out.writeInt(maxY);
            out.writeInt(palette.size());
            for (String entry : palette) {
                out.writeUTF(entry);
            }
            for (short index : indices) {
                out.writeShort(index);
            }
        }
    }

    private BlockData parseBlockData(String data) {
        try {
            return Bukkit.createBlockData(data);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeBlockDataString(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        if (data.startsWith("minecraft:iron_chain[")) {
            return "minecraft:chain" + data.substring("minecraft:iron_chain".length());
        }
        if (data.equals("minecraft:iron_chain")) {
            return "minecraft:chain";
        }
        return data;
    }

    private Long parseChunkKey(String fileName) {
        if (!fileName.startsWith("chunk_") || !fileName.endsWith(".bin")) {
            return null;
        }
        String name = fileName.substring(6, fileName.length() - 4);
        String[] parts = name.split("_");
        if (parts.length != 2) {
            return null;
        }
        try {
            int chunkX = Integer.parseInt(parts[0]);
            int chunkZ = Integer.parseInt(parts[1]);
            return toKey(chunkX, chunkZ);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static long toKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    public static int keyToChunkX(long key) {
        return (int) (key >> 32);
    }

    public static int keyToChunkZ(long key) {
        return (int) key;
    }

    public static final class SnapshotData {
        private final int minY;
        private final int maxY;
        private final BlockData[] palette;
        private final short[] indices;

        private SnapshotData(int minY, int maxY, BlockData[] palette, short[] indices) {
            this.minY = minY;
            this.maxY = maxY;
            this.palette = palette;
            this.indices = indices;
        }

        public int getMinY() {
            return minY;
        }

        public int getMaxY() {
            return maxY;
        }

        public BlockData[] getPalette() {
            return palette;
        }

        public short[] getIndices() {
            return indices;
        }
    }
}
