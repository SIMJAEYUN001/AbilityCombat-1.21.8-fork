package com.abilitycombat.gui;

import com.abilitycombat.ability.AbilityDefinition;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AbilitySelectGui implements InventoryHolder {

    private final UUID playerId;
    private final Inventory inventory;
    private final Map<Integer, AbilityDefinition> slotMap = new HashMap<>();
    private final Map<Integer, Integer> rerollSlotMap = new HashMap<>(); // reroll slot -> ability slot
    private int remainingRerolls;

    public AbilitySelectGui(UUID playerId, List<AbilityDefinition> options, int remainingRerolls) {
        this.playerId = playerId;
        this.remainingRerolls = remainingRerolls;
        this.inventory = Bukkit.createInventory(this, 18, Component.text("능력 선택"));
        int[] abilitySlots = { 2, 4, 6 };
        int[] rerollSlots = { 11, 13, 15 };
        for (int i = 0; i < options.size() && i < abilitySlots.length; i++) {
            AbilityDefinition ability = options.get(i);
            int abilitySlot = abilitySlots[i];
            int rerollSlot = rerollSlots[i];
            inventory.setItem(abilitySlot, AbilityItemFactory.create(ability));
            slotMap.put(abilitySlot, ability);

            if (remainingRerolls > 0) {
                inventory.setItem(rerollSlot, createRerollButton(remainingRerolls));
                rerollSlotMap.put(rerollSlot, abilitySlot);
            }
        }
    }

    public AbilitySelectGui(UUID playerId, List<AbilityDefinition> options) {
        this(playerId, options, 1);
    }

    private ItemStack createRerollButton(int count) {
        ItemStack rerollButton = new ItemStack(Material.BARRIER);
        ItemMeta meta = rerollButton.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§c재설정 (" + count + "회 남음)"));
            meta.lore(List.of(Component.text("§7이 능력을 다른 능력으로 교체합니다.")));
            rerollButton.setItemMeta(meta);
        }
        return rerollButton;
    }

    public void updateAbility(int abilitySlot, AbilityDefinition ability) {
        if (slotMap.containsKey(abilitySlot)) {
            inventory.setItem(abilitySlot, AbilityItemFactory.create(ability));
            slotMap.put(abilitySlot, ability);
        }
    }

    public void updateRerollButtons(int remainingCount) {
        this.remainingRerolls = remainingCount;
        for (int rerollSlot : rerollSlotMap.keySet()) {
            if (remainingCount > 0) {
                inventory.setItem(rerollSlot, createRerollButton(remainingCount));
            } else {
                inventory.setItem(rerollSlot, null);
            }
        }
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public AbilityDefinition getAbilityAt(int slot) {
        return slotMap.get(slot);
    }

    public boolean isRerollSlot(int slot) {
        return rerollSlotMap.containsKey(slot);
    }

    public int getAbilitySlotForReroll(int rerollSlot) {
        return rerollSlotMap.getOrDefault(rerollSlot, -1);
    }

    public AbilityDefinition getAbilityForReroll(int rerollSlot) {
        int abilitySlot = getAbilitySlotForReroll(rerollSlot);
        return abilitySlot >= 0 ? slotMap.get(abilitySlot) : null;
    }

    public boolean isRerollAvailable() {
        return remainingRerolls > 0;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
