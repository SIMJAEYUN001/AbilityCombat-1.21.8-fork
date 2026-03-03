package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;

@AbilityManifest(name = "호박 (Pumpkin)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[철괴 우클릭 - 호박 씬우기]§f §8(쿨타임: 40초)",
        "§7주변 §f30칸§7 이내의 모든 플레이어에게",
        "§6조각된 호박§7을 §f15초§7간 강제 착용시킵니다.",
        "",
        "§7호박에는 §c귀속 저주§7가 적용되어",
        "§7벗을 수 없습니다.",
        "",
        "§e§l[패시브 - 참수형]",
        "§7호박을 쓴 적을 공격할 때,",
        "§7상대의 체력이 §c20% 미만§7이면 §4처형§7합니다."
}, summarize = {
        "§7철괴 우클릭§f: 호박 강제 착용 (15초)",
        "§7패시브§f: 호박 쓴 적 20% 미만 시 처형"
})
public class Pumpkin extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 40;
    private static final int DURATION_SECONDS = 15;
    private static final double RANGE = 30.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final Map<Player, ItemStack> previousHelmets = new HashMap<>();
    private int remainingDurationTicks = 0;

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent e) {
            onDamageByEntity(e);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker) || !attacker.equals(getPlayer())) {
            return;
        }
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        // 호박을 쓴 상태인지 확인
        if (target.getInventory().getHelmet() == null ||
                target.getInventory().getHelmet().getType() != Material.CARVED_PUMPKIN) {
            return;
        }
        // 체력 20% 미만 시 처형
        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healthPercent = target.getHealth() / maxHealth;
        if (healthPercent < 0.2) {
            event.setCancelled(true);
            target.setHealth(0);
            attacker.sendMessage("§4참수형! §c" + target.getName() + "§4을(를) 처형했습니다.");
            target.sendMessage("§4호박의 저주로 참수형당했습니다!");
        }
    }

    public Pumpkin(Participant participant) {
        super(participant);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        restoreHelmets();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        applyPumpkins();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    private void applyPumpkins() {
        ItemStack pumpkin = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = pumpkin.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            pumpkin.setItemMeta(meta);
        }
        for (Player target : getPlayer().getWorld().getPlayers()) {
            if (target.equals(getPlayer())) {
                continue;
            }
            if (target.getLocation().distanceSquared(getPlayer().getLocation()) > RANGE * RANGE) {
                continue;
            }
            if (!previousHelmets.containsKey(target)) {
                previousHelmets.put(target, target.getInventory().getHelmet());
            }
            target.getInventory().setHelmet(pumpkin.clone());
        }
        remainingDurationTicks = DURATION_SECONDS * 20;
        registerTick();
    }

    @Override
    public void onTick(int tick) {
        if (remainingDurationTicks > 0) {
            remainingDurationTicks--;
            if (remainingDurationTicks <= 0) {
                restoreHelmets();
            }
        }
    }

    private void restoreHelmets() {
        for (Map.Entry<Player, ItemStack> entry : previousHelmets.entrySet()) {
            Player target = entry.getKey();
            if (target.getInventory().getHelmet() != null
                    && target.getInventory().getHelmet().getType() == Material.CARVED_PUMPKIN) {
                target.getInventory().setHelmet(entry.getValue());
            }
        }
        previousHelmets.clear();
    }
}
