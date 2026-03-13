package com.abilitycombat.gui;

import com.abilitycombat.ability.AbilityDefinition;
import com.abilitycombat.ability.AbilityRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AbilityDebugGui implements InventoryHolder {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final boolean viewOnly;
    private final Inventory inventory;
    private final Map<Integer, AbilityDefinition> slotMap = new HashMap<>();
    private final List<AbilityDefinition> abilities;
    private final AbilityRegistry abilityRegistry;
    private final int page;
    private final int pageCount;

    public AbilityDebugGui(List<AbilityDefinition> abilities, int page) {
        this(abilities, null, page, false);
    }

    public AbilityDebugGui(List<AbilityDefinition> abilities, int page, boolean viewOnly) {
        this(abilities, null, page, viewOnly);
    }

    public AbilityDebugGui(List<AbilityDefinition> abilities, AbilityRegistry abilityRegistry, int page, boolean viewOnly) {
        this.abilities = abilities == null ? Collections.emptyList() : abilities;
        this.abilityRegistry = abilityRegistry;
        this.viewOnly = viewOnly;
        this.pageCount = Math.max(1, (int) Math.ceil(this.abilities.size() / (double) PAGE_SIZE));
        this.page = Math.max(0, Math.min(page, pageCount - 1));
        String title = viewOnly ? "능력 목록" : "능력 디버그";
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text(title + " (" + (this.page + 1) + "/" + pageCount + ")"));
        build();
    }

    public AbilityDefinition getAbilityAt(int slot) {
        return slotMap.get(slot);
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return pageCount;
    }

    public boolean isViewOnly() {
        return viewOnly;
    }

    public boolean isPrevSlot(int slot) {
        return slot == SLOT_PREV;
    }

    public boolean isNextSlot(int slot) {
        return slot == SLOT_NEXT;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void build() {
        slotMap.clear();
        int start = page * PAGE_SIZE;
        int end = Math.min(abilities.size(), start + PAGE_SIZE);
        int slot = 0;
        for (int i = start; i < end; i++) {
            AbilityDefinition ability = abilities.get(i);
            inventory.setItem(slot, AbilityItemFactory.createForDebug(ability, abilityRegistry));
            slotMap.put(slot, ability);
            slot++;
        }
        inventory.setItem(SLOT_PREV, createNavItem(Material.ARROW, "§7이전 페이지"));
        inventory.setItem(SLOT_INFO, createNavItem(Material.PAPER, "§f" + (page + 1) + " / " + pageCount));
        inventory.setItem(SLOT_NEXT, createNavItem(Material.ARROW, "§7다음 페이지"));
    }

    private ItemStack createNavItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(toComponent(name));
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
}
