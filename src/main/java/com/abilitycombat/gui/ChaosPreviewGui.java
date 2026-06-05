package com.abilitycombat.gui;

import com.abilitycombat.ability.AbilityDefinition;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

public class ChaosPreviewGui implements InventoryHolder {

    private final UUID playerId;
    private final Inventory inventory;

    public ChaosPreviewGui(UUID playerId, AbilityDefinition first, AbilityDefinition second) {
        this.playerId = playerId;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("혼돈 능력 확인"));
        inventory.setItem(11, labeled(Material.IRON_INGOT, "§f1번 능력", List.of("§7원래 능력 방식으로 발동합니다")));
        inventory.setItem(12, AbilityItemFactory.create(first));
        inventory.setItem(14, labeled(Material.GOLD_INGOT, "§62번 능력 §7(금괴)", List.of("§7금괴로 발동합니다")));
        inventory.setItem(15, AbilityItemFactory.create(second));
        inventory.setItem(22, labeled(Material.BOOK, "§e닫으면 선택 확정", List.of("§7설명을 읽은 뒤 GUI를 닫으세요")));
    }

    private ItemStack labeled(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            meta.lore(lore.stream().map(Component::text).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
