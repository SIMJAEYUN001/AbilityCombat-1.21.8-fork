package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.Participant;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@AbilityManifest(name = "급조모루 (MakeshiftAnvil)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 급조 강화]",
        "§7페이즈가 넘어가거나 적을 처치할 때마다",
        "§7착용 중인 갑옷/들고 있는 검 중 무작위 1개를 강화합니다",
        "",
        "§7철 장비는 §b다이아 장비§7로, 다이아 장비는 §8네더라이트 장비§7로 강화됩니다",
        "§7이미 네더라이트 장비는 강화 대상에서 제외됩니다",
        "§7강화 시 기존 인챈트는 전승되며, §5소실 저주§7가 부여됩니다",
        "",
        "§e§l[패시브 - 복수의 모루]",
        "§7이 능력 보유자를 다른 플레이어가 처치하면",
        "§7처치자의 장비도 동일한 규칙으로 무작위 강화됩니다"
}, summarize = {
        "§7패시브§f: 페이즈/처치 시 갑옷·검 1개 무작위 강화",
        "§7강화§f: 철→다이아, 다이아→네더라이트",
        "§7부가효과§f: 인챈트 전승 + 소실 저주",
        "§7사망 시§f: 처치자 장비도 동일 강화"
})
public class MakeshiftAnvil extends AbilityBase {

    private final Random random = new Random();
    private int lastPhaseIndex = -1;

    public MakeshiftAnvil(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(PlayerDeathEvent.class);
        lastPhaseIndex = -1;
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof PlayerDeathEvent deathEvent) {
            onPlayerDeath(deathEvent);
        }
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 != 0) {
            return;
        }
        int currentPhase = Math.max(1, getCurrentPhaseIndex());

        // 게임 재시작 등으로 페이즈 인덱스가 내려간 경우 기준점을 재동기화한다
        if (lastPhaseIndex < 0 || currentPhase < lastPhaseIndex) {
            lastPhaseIndex = currentPhase;
            return;
        }

        if (currentPhase == lastPhaseIndex) {
            return;
        }

        int phaseDiff = currentPhase - lastPhaseIndex;
        lastPhaseIndex = currentPhase;
        for (int i = 0; i < phaseDiff; i++) {
            upgradeRandomEquipment(getPlayer(), "페이즈 전환");
        }
    }

    private void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) {
            return;
        }

        Player owner = getPlayer();
        if (killer.equals(owner)) {
            upgradeRandomEquipment(owner, "플레이어 처치");
        } else if (dead.equals(owner)) {
            upgradeRandomEquipment(killer, "급조모루 처치 보상");
        }
    }

    private boolean upgradeRandomEquipment(Player target, String triggerReason) {
        List<UpgradeCandidate> candidates = collectCandidates(target);
        if (candidates.isEmpty()) {
            return false;
        }

        UpgradeCandidate selected = candidates.get(random.nextInt(candidates.size()));
        ItemStack baseItem = selected.item();
        ItemStack upgraded = new ItemStack(selected.upgradedType(), baseItem.getAmount());
        ItemMeta baseMeta = baseItem.getItemMeta();
        if (baseMeta != null) {
            upgraded.setItemMeta(baseMeta.clone());
        }

        ItemMeta meta = upgraded.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
            upgraded.setItemMeta(meta);
        } else {
            upgraded.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        }

        applyToSlot(target.getInventory(), selected.slot(), upgraded);
        target.sendMessage("§6[급조모루] §f" + triggerReason + "으로 §e" + selected.item().getType().name()
                + "§f → §b" + selected.upgradedType().name() + "§f 강화됨");
        return true;
    }

    private List<UpgradeCandidate> collectCandidates(Player player) {
        PlayerInventory inventory = player.getInventory();
        List<UpgradeCandidate> candidates = new ArrayList<>();

        addCandidate(candidates, Slot.HELMET, inventory.getHelmet());
        addCandidate(candidates, Slot.CHESTPLATE, inventory.getChestplate());
        addCandidate(candidates, Slot.LEGGINGS, inventory.getLeggings());
        addCandidate(candidates, Slot.BOOTS, inventory.getBoots());
        addCandidate(candidates, Slot.MAIN_HAND, inventory.getItemInMainHand());
        addCandidate(candidates, Slot.OFF_HAND, inventory.getItemInOffHand());

        return candidates;
    }

    private void addCandidate(List<UpgradeCandidate> candidates, Slot slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        Material upgradedType = getUpgradedType(item.getType());
        if (upgradedType == null) {
            return;
        }
        candidates.add(new UpgradeCandidate(slot, item, upgradedType));
    }

    private Material getUpgradedType(Material type) {
        return switch (type) {
            case IRON_HELMET -> Material.DIAMOND_HELMET;
            case IRON_CHESTPLATE -> Material.DIAMOND_CHESTPLATE;
            case IRON_LEGGINGS -> Material.DIAMOND_LEGGINGS;
            case IRON_BOOTS -> Material.DIAMOND_BOOTS;
            case IRON_SWORD -> Material.DIAMOND_SWORD;

            case DIAMOND_HELMET -> Material.NETHERITE_HELMET;
            case DIAMOND_CHESTPLATE -> Material.NETHERITE_CHESTPLATE;
            case DIAMOND_LEGGINGS -> Material.NETHERITE_LEGGINGS;
            case DIAMOND_BOOTS -> Material.NETHERITE_BOOTS;
            case DIAMOND_SWORD -> Material.NETHERITE_SWORD;
            default -> null;
        };
    }

    private void applyToSlot(PlayerInventory inventory, Slot slot, ItemStack item) {
        switch (slot) {
            case HELMET -> inventory.setHelmet(item);
            case CHESTPLATE -> inventory.setChestplate(item);
            case LEGGINGS -> inventory.setLeggings(item);
            case BOOTS -> inventory.setBoots(item);
            case MAIN_HAND -> inventory.setItemInMainHand(item);
            case OFF_HAND -> inventory.setItemInOffHand(item);
        }
    }

    private int getCurrentPhaseIndex() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return 0;
        }
        GameManager gameManager = plugin.getGameManager();
        if (gameManager == null) {
            return 0;
        }
        return Math.max(0, gameManager.getCurrentPhaseIndex());
    }

    private enum Slot {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS,
        MAIN_HAND,
        OFF_HAND
    }

    private record UpgradeCandidate(Slot slot, ItemStack item, Material upgradedType) {
    }
}
