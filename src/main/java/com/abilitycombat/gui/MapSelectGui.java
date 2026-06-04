package com.abilitycombat.gui;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.game.MatchMode;
import com.abilitycombat.game.MapData;
import com.abilitycombat.game.MapManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 게임 시작 시 맵 선택 GUI
 */
public class MapSelectGui implements InventoryHolder {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int RANDOM_SLOT = 4; // 상단 중앙
    private static final int MODE_TOGGLE_SLOT = 8; // 우측 상단

    private final AbilityCombat plugin;
    private final Inventory inventory;
    private final List<MapData> mapList = new ArrayList<>();
    private MatchMode selectedMode;

    public MapSelectGui(AbilityCombat plugin, MatchMode selectedMode) {
        this.plugin = plugin;
        this.selectedMode = selectedMode;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("맵 선택"));
        build();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 랜덤 맵 슬롯인지 확인
     */
    public boolean isRandomSlot(int slot) {
        return slot == RANDOM_SLOT;
    }

    public boolean isModeToggleSlot(int slot) {
        return slot == MODE_TOGGLE_SLOT;
    }

    public MatchMode getSelectedMode() {
        return selectedMode;
    }

    public void toggleMode() {
        selectedMode = switch (selectedMode) {
            case SOLO -> MatchMode.DUO;
            case DUO -> MatchMode.TEAM;
            case TEAM -> MatchMode.SOLO;
        };
        inventory.setItem(MODE_TOGGLE_SLOT, createModeItem());
    }

    /**
     * 슬롯에 해당하는 맵 데이터 반환 (없으면 null)
     * 맵 목록은 슬롯 9번부터 시작
     */
    public MapData getMapAt(int slot) {
        int index = slot - 9;
        if (index < 0 || index >= mapList.size()) {
            return null;
        }
        return mapList.get(index);
    }

    /**
     * 등록된 맵이 있는지 확인
     */
    public boolean hasMaps() {
        return !mapList.isEmpty();
    }

    private void build() {
        MapManager mapManager = plugin.getMapManager();
        if (mapManager == null) {
            return;
        }

        // 랜덤 맵 선택 버튼
        inventory.setItem(RANDOM_SLOT, createItem(Material.ENDER_PEARL, "§d랜덤 맵", List.of(
                "§7등록된 맵 중 랜덤으로 선택합니다.",
                "",
                "§e클릭하여 랜덤 맵으로 게임 시작")));
        inventory.setItem(MODE_TOGGLE_SLOT, createModeItem());

        // 맵 목록 (9번 슬롯부터)
        int slot = 9;
        for (MapData map : mapManager.getAllMaps()) {
            if (slot >= SIZE) {
                break;
            }
            mapList.add(map);
            inventory.setItem(slot, createMapItem(map));
            slot++;
        }

        // 맵이 없는 경우 안내
        if (mapList.isEmpty()) {
            inventory.setItem(22, createItem(Material.BARRIER, "§c등록된 맵이 없습니다", List.of(
                    "§7/aw config에서 맵을 추가해주세요.")));
        }
    }

    private ItemStack createModeItem() {
        Material icon = switch (selectedMode) {
            case SOLO -> Material.IRON_SWORD;
            case DUO -> Material.CYAN_BANNER;
            case TEAM -> Material.RED_BANNER;
        };
        String color = switch (selectedMode) {
            case SOLO -> "§f";
            case DUO -> "§b";
            case TEAM -> "§c";
        };
        return createItem(icon, "§b게임 모드", List.of(
                "§f현재: " + color + selectedMode.getDisplayName(),
                "",
                "§7클릭: 개인전 / 2인전 / 팀전 전환"));
    }

    private ItemStack createMapItem(MapData map) {
        String locationLine = String.format("§8%s (%.0f, %.0f, %.0f)",
                map.getWorldName(), map.getX(), map.getY(), map.getZ());
        return createItem(Material.FILLED_MAP, "§e" + map.getName(), List.of(
                locationLine,
                "",
                "§a클릭하여 이 맵으로 게임 시작"));
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(toComponent(name));
            if (lore != null) {
                meta.lore(toComponents(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Component toComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_SERIALIZER.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    private static List<Component> toComponents(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(toComponent(line));
        }
        return components;
    }
}
