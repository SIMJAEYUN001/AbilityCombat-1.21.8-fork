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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        File file = new File(plugin.getDataFolder(), DATA_FILE);
        if (!file.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        level = Math.max(0, config.getInt("level", 0));
        List<?> list = config.getList("toolkit");
        if (list == null) {
            return;
        }

        int slot = ITEM_START_SLOT;
        for (Object obj : list) {
            if (obj instanceof ItemStack item && slot < inventory.getSize()) {
                inventory.setItem(slot++, item);
            }
        }
    }

    /**
     * 저장된 기본 지급템 목록 반환
     */
    public static List<ItemStack> getToolkitItems(AbilityCombat plugin) {
        File file = new File(plugin.getDataFolder(), DATA_FILE);
        if (!file.exists()) {
            return getDefaultItems();
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<?> list = config.getList("toolkit");
        if (list == null || list.isEmpty()) {
            return getDefaultItems();
        }

        List<ItemStack> result = new ArrayList<>();
        for (Object obj : list) {
            if (obj instanceof ItemStack item) {
                result.add(item.clone());
            }
        }

        return result.isEmpty() ? getDefaultItems() : result;
    }

    public static int getToolkitLevel(AbilityCombat plugin) {
        File file = new File(plugin.getDataFolder(), DATA_FILE);
        if (!file.exists()) {
            return 0;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        return Math.max(0, config.getInt("level", 0));
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
}
