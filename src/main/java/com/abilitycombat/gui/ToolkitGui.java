package com.abilitycombat.gui;

import com.abilitycombat.AbilityCombat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기본 지급템 설정 GUI
 * 인벤토리에 아이템을 넣고 닫으면 자동 저장됩니다.
 */
public class ToolkitGui implements InventoryHolder {

    private static final String DATA_FILE = "toolkit.yml";
    private static final int INVENTORY_SIZE = 45; // 5줄
    private static final int CONTROL_ROW_SIZE = 9;
    private static final int ITEM_START_SLOT = 9;
    private static final int LEVEL_SLOT = 4;
    private static final int MAX_LEVEL = 1000;

    private final AbilityCombat plugin;
    private final Inventory inventory;
    private int level;

    public ToolkitGui(AbilityCombat plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, Component.text("기본 지급템 설정", NamedTextColor.GOLD));
        loadItems();
        refreshLevelItem();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * GUI 내용을 파일에 저장
     */
    public void saveItems() {
        File file = new File(plugin.getDataFolder(), DATA_FILE);
        FileConfiguration config = new YamlConfiguration();

        List<ItemStack> items = new ArrayList<>();
        for (int i = ITEM_START_SLOT; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                items.add(item);
            }
        }

        config.set("toolkit", items);
        config.set("level", level);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("툴킷 저장 실패: " + e.getMessage());
        }
    }

    /**
     * 파일에서 아이템 불러오기
     */
    private void loadItems() {
        ToolkitData toolkitData = loadToolkitData(plugin);
        level = toolkitData.level();
        if (toolkitData.items().isEmpty()) {
            return;
        }

        int slot = ITEM_START_SLOT;
        for (ItemStack item : toolkitData.items()) {
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot++, item);
        }
    }

    /**
     * 저장된 기본 지급템 목록 반환
     */
    public static List<ItemStack> getToolkitItems(AbilityCombat plugin) {
        ToolkitData toolkitData = loadToolkitData(plugin);
        if (toolkitData.items().isEmpty()) {
            return getDefaultItems();
        }

        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : toolkitData.items()) {
            result.add(item.clone());
        }

        return result.isEmpty() ? getDefaultItems() : result;
    }

    public static int getToolkitLevel(AbilityCombat plugin) {
        return loadToolkitData(plugin).level();
    }

    public boolean isControlSlot(int slot) {
        return slot >= 0 && slot < CONTROL_ROW_SIZE;
    }

    public boolean isLevelSlot(int slot) {
        return slot == LEVEL_SLOT;
    }

    public int getLevel() {
        return level;
    }

    public void adjustLevel(int delta) {
        level = Math.max(0, Math.min(MAX_LEVEL, level + delta));
        refreshLevelItem();
    }

    public void refreshLevelItem() {
        inventory.setItem(LEVEL_SLOT, createLevelItem());
    }

    private ItemStack createLevelItem() {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(text("레벨 설정", NamedTextColor.YELLOW));
            List<Component> lore = new ArrayList<>();
            lore.add(text("현재 레벨: " + level, NamedTextColor.WHITE));
            lore.add(text("좌클릭: +1, 우클릭: -1", NamedTextColor.GRAY));
            lore.add(text("쉬프트: +5 / -5", NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * 기본 아이템 (설정이 없을 때)
     */
    private static List<ItemStack> getDefaultItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Material.IRON_SWORD));
        items.add(new ItemStack(Material.BOW));
        items.add(new ItemStack(Material.ARROW, 32));
        items.add(new ItemStack(Material.IRON_INGOT));
        items.add(new ItemStack(Material.COOKED_BEEF, 16));
        items.add(new ItemStack(Material.GOLDEN_APPLE, 3));
        return items;
    }

    private static ToolkitData loadToolkitData(AbilityCombat plugin) {
        File file = new File(plugin.getDataFolder(), DATA_FILE);
        if (!file.exists()) {
            return new ToolkitData(List.of(), 0);
        }

        try {
            String raw = Files.readString(file.toPath());
            Object loaded = new Yaml().load(raw);
            if (!(loaded instanceof Map<?, ?> root)) {
                return new ToolkitData(List.of(), 0);
            }

            int level = readLevel(root.get("level"));
            List<ItemStack> items = new ArrayList<>();
            boolean removedUnsupported = false;

            Object toolkitSection = root.get("toolkit");
            if (toolkitSection instanceof List<?> entries) {
                for (Object entry : entries) {
                    if (!(entry instanceof Map<?, ?> entryMap)) {
                        removedUnsupported = true;
                        continue;
                    }

                    ItemStack item = deserializeItem(entryMap);
                    if (item == null || item.getType() == Material.AIR) {
                        removedUnsupported = true;
                        continue;
                    }
                    items.add(item);
                }
            }

            if (removedUnsupported) {
                saveSanitizedToolkit(plugin, items, level);
                plugin.getLogger().warning("지원되지 않는 툴킷 아이템을 toolkit.yml에서 자동으로 제거했습니다.");
            }

            return new ToolkitData(items, level);
        } catch (Exception exception) {
            plugin.getLogger().warning("툴킷 로드 실패: " + exception.getMessage());
            return new ToolkitData(List.of(), 0);
        }
    }

    private static int readLevel(Object levelValue) {
        if (levelValue instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (levelValue instanceof String text) {
            try {
                return Math.max(0, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static ItemStack deserializeItem(Map<?, ?> rawItem) {
        try {
            Map<String, Object> serialized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawItem.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    serialized.put(key, entry.getValue());
                }
            }
            return ItemStack.deserialize(serialized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void saveSanitizedToolkit(AbilityCombat plugin, List<ItemStack> items, int level) {
        File file = new File(plugin.getDataFolder(), DATA_FILE);
        FileConfiguration config = new YamlConfiguration();
        config.set("toolkit", items);
        config.set("level", level);
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("정리된 툴킷 저장 실패: " + exception.getMessage());
        }
    }

    private record ToolkitData(List<ItemStack> items, int level) {
    }
}
