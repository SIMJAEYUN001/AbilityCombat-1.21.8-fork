package com.abilitycombat.gui;

import com.abilitycombat.AbilityCombat;
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
 * 맵 관리 GUI - 맵 추가/삭제/이름 변경
 */
public class MapConfigGui implements InventoryHolder {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int ADD_MAP_SLOT = 49; // 하단 중앙
    private static final int BACK_SLOT = 45; // 하단 좌측

    private final AbilityCombat plugin;
    private final Inventory inventory;
    private final List<MapData> mapList = new ArrayList<>();

    public MapConfigGui(AbilityCombat plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("맵 관리"));
        build();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 슬롯에 해당하는 맵 데이터 반환 (없으면 null)
     */
    public MapData getMapAt(int slot) {
        if (slot < 0 || slot >= mapList.size()) {
            return null;
        }
        return mapList.get(slot);
    }

    /**
     * 현재 위치 추가 슬롯인지 확인
     */
    public boolean isAddMapSlot(int slot) {
        return slot == ADD_MAP_SLOT;
    }

    /**
     * 뒤로 가기 슬롯인지 확인
     */
    public boolean isBackSlot(int slot) {
        return slot == BACK_SLOT;
    }

    /**
     * GUI 새로고침
     */
    public void refresh() {
        inventory.clear();
        mapList.clear();
        build();
    }

    private void build() {
        MapManager mapManager = plugin.getMapManager();
        if (mapManager == null) {
            return;
        }

        // 맵 목록 추가 (최대 45개)
        int slot = 0;
        for (MapData map : mapManager.getAllMaps()) {
            if (slot >= 45) {
                break;
            }
            mapList.add(map);
            inventory.setItem(slot, createMapItem(map));
            slot++;
        }

        // 현재 위치 추가 버튼
        inventory.setItem(ADD_MAP_SLOT, createItem(Material.LIME_CONCRETE, "§a현재 위치 추가", List.of(
                "§7클릭하여 현재 위치를 새 맵으로 추가합니다.",
                "§7맵 이름은 자동 생성됩니다.")));

        // 뒤로 가기 버튼
        inventory.setItem(BACK_SLOT, createItem(Material.ARROW, "§7뒤로 가기", List.of(
                "§7설정 화면으로 돌아갑니다.")));
    }

    private ItemStack createMapItem(MapData map) {
        String locationLine = String.format("§8%s (%.0f, %.0f, %.0f)",
                map.getWorldName(), map.getX(), map.getY(), map.getZ());
        return createItem(Material.FILLED_MAP, "§e" + map.getName(), List.of(
                locationLine,
                "",
                "§7좌클릭: 이 맵으로 텔레포트",
                "§7우클릭: 맵 삭제",
                "§7쉬프트+클릭: 이름 변경"));
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
