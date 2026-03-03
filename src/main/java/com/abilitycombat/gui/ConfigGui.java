package com.abilitycombat.gui;

import com.abilitycombat.AbilityCombat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigGui implements InventoryHolder {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    public enum Type {
        ATTACK_COOLDOWN,
        LOBBY_BLOCK_BREAK,
        LOBBY_BLOCK_PLACE,
        LOBBY_INVINCIBILITY,
        LOBBY_LOCATION,
        MAP_MANAGE,
        INVINCIBILITY,
        GAME_DURATION,
        SELECTION_TIME,
        BORDER_INITIAL_RADIUS,
        BORDER_SHRINK_SECONDS,
        SPECTATOR_HIDE,
        CRAFTING,
        MAP_RESTORE,
        MAP_RESTORE_RUN,
        MOB_SPAWN_BLOCK,
        INFINITE_DURABILITY,
        PHASE,
        REROLL_COUNT,
        RELOAD
    }

    public static final class Entry {
        private final Type type;
        private final int index;
        private final int slot;

        private Entry(Type type, int index, int slot) {
            this.type = type;
            this.index = index;
            this.slot = slot;
        }

        public Type getType() {
            return type;
        }

        public int getIndex() {
            return index;
        }

        public int getSlot() {
            return slot;
        }
    }

    private final AbilityCombat plugin;
    private final Inventory inventory;
    private final Map<Integer, Entry> entryMap = new HashMap<>();

    public ConfigGui(AbilityCombat plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("게임 설정"));
        build();
    }

    public Entry getEntryAt(int slot) {
        return entryMap.get(slot);
    }

    public void refresh(Entry entry) {
        inventory.setItem(entry.getSlot(), buildItem(entry));
    }

    public void refreshAll() {
        for (Entry entry : entryMap.values()) {
            refresh(entry);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void build() {
        addEntry(0, Type.ATTACK_COOLDOWN, -1);
        addEntry(1, Type.LOBBY_BLOCK_BREAK, -1);
        addEntry(2, Type.LOBBY_BLOCK_PLACE, -1);
        addEntry(3, Type.LOBBY_INVINCIBILITY, -1);
        addEntry(4, Type.LOBBY_LOCATION, -1);
        addEntry(10, Type.MAP_MANAGE, -1);
        addEntry(11, Type.INVINCIBILITY, -1);
        addEntry(12, Type.GAME_DURATION, -1);
        addEntry(13, Type.SELECTION_TIME, -1);
        addEntry(26, Type.REROLL_COUNT, -1);
        addEntry(14, Type.BORDER_INITIAL_RADIUS, -1);
        addEntry(15, Type.BORDER_SHRINK_SECONDS, -1);
        addEntry(16, Type.SPECTATOR_HIDE, -1);
        addEntry(17, Type.CRAFTING, -1);
        addEntry(18, Type.MOB_SPAWN_BLOCK, -1);
        addEntry(24, Type.INFINITE_DURABILITY, -1);
        addEntry(19, Type.PHASE, 0);
        addEntry(20, Type.PHASE, 1);
        addEntry(21, Type.PHASE, 2);
        addEntry(22, Type.PHASE, 3);
        addEntry(23, Type.PHASE, 4);
        addEntry(25, Type.RELOAD, -1);
    }

    private void addEntry(int slot, Type type, int index) {
        Entry entry = new Entry(type, index, slot);
        entryMap.put(slot, entry);
        inventory.setItem(slot, buildItem(entry));
    }

    private ItemStack buildItem(Entry entry) {
        FileConfiguration config = plugin.getConfig();
        return switch (entry.getType()) {
            case ATTACK_COOLDOWN -> {
                boolean enabled = config.getBoolean("combat.attack-cooldown", true);
                Material icon = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
                yield createItem(icon, "§b공격 쿨타임", List.of(
                        "§f" + (enabled ? "ON" : "OFF"),
                        "§7클릭: 토글",
                        "§8OFF: 바닐라 공격속도 제한 제거"));
            }
            case LOBBY_BLOCK_BREAK -> {
                boolean allow = config.getBoolean("lobby.allow-block-break", true);
                yield createItem(Material.IRON_PICKAXE, "§b대기중 블럭 파괴", List.of(
                        "§f" + (allow ? "허용" : "차단"),
                        "§7클릭: 토글",
                        "§8게임 진행중에는 적용되지 않음"));
            }
            case LOBBY_BLOCK_PLACE -> {
                boolean allow = config.getBoolean("lobby.allow-block-place", true);
                yield createItem(Material.BRICKS, "§b대기중 블럭 설치", List.of(
                        "§f" + (allow ? "허용" : "차단"),
                        "§7클릭: 토글",
                        "§8게임 진행중에는 적용되지 않음"));
            }
            case LOBBY_INVINCIBILITY -> {
                boolean enabled = config.getBoolean("lobby.invincible", false);
                yield createItem(Material.SHIELD, "§b대기중 무적", List.of(
                        "§f" + (enabled ? "활성화" : "비활성화"),
                        "§7클릭: 토글",
                        "§8게임 진행중에는 적용되지 않음"));
            }
            case LOBBY_LOCATION -> {
                org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("lobby.location");
                String world = section != null ? section.getString("world", "").trim() : "";
                boolean configured = world != null && !world.isEmpty();
                String pos = configured
                        ? "§f" + world + " §7(" + section.getDouble("x", 0) + ", "
                                + section.getDouble("y", 0) + ", " + section.getDouble("z", 0) + ")"
                        : "§c미설정";
                yield createItem(Material.RED_BED, "§b로비 위치", List.of(
                        pos,
                        "§7클릭: 현재 위치로 설정",
                        "§8게임 미진행/종료 시 플레이어 집결 위치"));
            }
            case MAP_MANAGE -> {
                int mapCount = plugin.getMapManager() != null ? plugin.getMapManager().getMapCount() : 0;
                yield createItem(Material.COMPASS, "§a맵 관리", List.of(
                        "§f등록된 맵: §e" + mapCount + "개",
                        "§7클릭: 맵 관리 GUI 열기"));
            }
            case INVINCIBILITY -> {
                int seconds = config.getInt("game.invincibility-seconds", 180);
                yield createItem(Material.CLOCK, "§b무적 시간", List.of(
                        "§f" + seconds + "초",
                        "§7좌클릭: +10초, 우클릭: -10초",
                        "§7쉬프트: +60 / -60"));
            }
            case GAME_DURATION -> {
                int seconds = config.getInt("game.duration-seconds", 720);
                yield createItem(Material.DIAMOND, "§b게임 시간", List.of(
                        "§f" + seconds + "초",
                        "§7좌클릭: +60초, 우클릭: -60초",
                        "§7쉬프트: +300 / -300"));
            }
            case SELECTION_TIME -> {
                int seconds = config.getInt("ability.selection-seconds", 15);
                yield createItem(Material.PAPER, "§b능력 선택 시간", List.of(
                        "§f" + seconds + "초",
                        "§7좌클릭: +5초, 우클릭: -5초",
                        "§7쉬프트: +10 / -10"));
            }
            case BORDER_INITIAL_RADIUS -> {
                int radius = config.getInt("world-border.initial-radius", 200);
                yield createItem(Material.MAP, "§b월드보더 초기 반지름", List.of(
                        "§f" + radius + "칸",
                        "§7좌클릭: +10, 우클릭: -10",
                        "§7쉬프트: +50 / -50"));
            }
            case BORDER_SHRINK_SECONDS -> {
                int speed = config.getInt("world-border.shrink-seconds", 6);
                yield createItem(Material.PISTON, "§b월드보더 축소 속도", List.of(
                        "§f" + speed + " 블록/초",
                        "§7좌클릭: +1, 우클릭: -1",
                        "§7쉬프트: +5 / -5"));
            }
            case SPECTATOR_HIDE -> {
                boolean hide = config.getBoolean("spectator.hide-from-alive", true);
                yield createItem(Material.ENDER_EYE, "§b관전자 숨김", List.of(
                        "§f" + (hide ? "활성화" : "비활성화"),
                        "§7클릭: 토글"));
            }
            case CRAFTING -> {
                boolean enabled = config.getBoolean("crafting.enabled", true);
                yield createItem(Material.CRAFTING_TABLE, "§b아이템 제작", List.of(
                        "§f" + (enabled ? "허용" : "차단"),
                        "§7클릭: 토글",
                        "§8제작대/인벤토리 제작"));
            }
            case MAP_RESTORE -> {
                boolean enabled = config.getBoolean("map-restore.enabled", true);
                yield createItem(Material.GRASS_BLOCK, "§b맵 복원", List.of(
                        "§f" + (enabled ? "활성화" : "비활성화"),
                        "§7클릭: 토글"));
            }
            case MAP_RESTORE_RUN -> createItem(Material.ANVIL, "§b맵 복원 실행", List.of(
                    "§7저장된 스냅샷으로 복원",
                    "§7클릭: 복원 시작"));
            case MOB_SPAWN_BLOCK -> {
                boolean blocked = config.getBoolean("mob-spawn.block-natural", true);
                yield createItem(Material.SPAWNER, "§b자연 몹 스폰 차단", List.of(
                        "§f" + (blocked ? "차단 중" : "허용 중"),
                        "§7클릭: 토글",
                        "§8능력으로 생성된 몹은 영향 없음"));
            }
            case INFINITE_DURABILITY -> {
                boolean enabled = config.getBoolean("durability.infinite", true);
                yield createItem(Material.DIAMOND_CHESTPLATE, "§b내구도 무제한", List.of(
                        "§f" + (enabled ? "활성화" : "비활성화"),
                        "§7클릭: 토글"));
            }
            case PHASE -> {
                PhaseData phase = getPhase(entry.getIndex());
                yield createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§d페이즈 " + (entry.getIndex() + 1), List.of(
                        "§f시간: " + phase.time + "초",
                        "§f반지름: " + phase.radius + "칸",
                        "§7좌클릭: 반지름 +10 / 우클릭: -10",
                        "§7쉬프트: 시간 +60 / -60"));
            }
            case REROLL_COUNT -> {
                int count = config.getInt("ability.reroll-count", 1);
                yield createItem(Material.BARRIER, "§b능력 재설정 횟수", List.of(
                        "§f" + count + "회",
                        "§7좌클릭: +1, 우클릭: -1"));
            }
            case RELOAD -> createItem(Material.BOOK, "§e설정 리로드", List.of(
                    "§7클릭: config.yml 다시 불러오기"));
        };
    }

    private PhaseData getPhase(int index) {
        FileConfiguration config = plugin.getConfig();
        List<Map<?, ?>> list = config.getMapList("world-border.phases");
        int radiusDefault = config.getInt("world-border.initial-radius", 200);
        if (index < 0 || index >= list.size()) {
            return new PhaseData(0, radiusDefault);
        }
        Map<?, ?> entry = list.get(index);
        int time = toInt(entry.get("time"), 0);
        int radius = toInt(entry.get("radius"), radiusDefault);
        return new PhaseData(time, radius);
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
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

    private static final class PhaseData {
        private final int time;
        private final int radius;

        private PhaseData(int time, int radius) {
            this.time = time;
            this.radius = radius;
        }
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
