package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

@AbilityManifest(name = "복서 (Boxer)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 맨주먹]",
        "§7게임 시작 시 인벤토리의 §f검을 모두 회수§7합니다",
        "§7공격 사거리가 §c50% 감소§7합니다",
        "§7빈손 타격 성공 시 짧게 전진하고 §c추가 피해 8§7을 줍니다",
        "",
        "§e§l[철괴 우클릭 - 풋워크]§f §8(쿨타임: 30초)",
        "§7자신에게 §b신속 II 8초§7를 부여합니다"
}, summarize = {
        "§7패시브§f: 검 회수, 사거리 50% 감소",
        "§7빈손 타격§f: 소폭 돌진 + 추가 피해 8",
        "§7철괴 우클릭§f: 신속 II 8초 (30초)"
})
public class Boxer extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 30;
    private static final int SPEED_DURATION_TICKS = 160;
    private static final double RANGE_PENALTY_SCALAR = -0.5;
    private static final double EXTRA_DAMAGE = 8.0;
    private static final double LUNGE_SPEED = 0.42;
    private static final double LUNGE_Y = 0.06;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);

    public Boxer(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        removeSwords();
        applyRangePenalty();
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        removeRangePenalty();
    }

    @Override
    protected void onDestroy() {
        removeRangePenalty();
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
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, SPEED_DURATION_TICKS, 1, true, false));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.65f, 1.35f);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageByEntityEvent damageEvent)
                || (event instanceof Cancellable cancellable && cancellable.isCancelled())) {
            return;
        }
        Player player = getPlayer();
        if (player == null || !damageEvent.getDamager().equals(player)) {
            return;
        }
        if (!(damageEvent.getEntity() instanceof LivingEntity target)
                || !LocationUtil.isValidTarget(player, target)
                || !isBareHand(player.getInventory().getItemInMainHand())) {
            return;
        }
        addOutgoingDamage(damageEvent, EXTRA_DAMAGE);
        lungeToward(player, target);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.7f, 1.45f);
    }

    private void removeSwords() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && isSword(item.getType())) {
                contents[i] = null;
            }
        }
        inventory.setContents(contents);
    }

    private boolean isBareHand(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private boolean isSword(Material material) {
        return material == Material.WOODEN_SWORD
                || material == Material.STONE_SWORD
                || material == Material.IRON_SWORD
                || material == Material.GOLDEN_SWORD
                || material == Material.DIAMOND_SWORD
                || material == Material.NETHERITE_SWORD;
    }

    private void lungeToward(Player player, LivingEntity target) {
        Vector direction = target.getLocation().toVector().subtract(player.getLocation().toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() < 1.0E-4) {
            return;
        }
        Vector velocity = direction.normalize().multiply(LUNGE_SPEED);
        velocity.setY(Math.max(player.getVelocity().getY(), LUNGE_Y));
        player.setVelocity(velocity);
    }

    private void applyRangePenalty() {
        Player player = getPlayer();
        NamespacedKey key = rangeKey();
        if (player == null || key == null) {
            return;
        }
        AttributeInstance range = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (range == null) {
            return;
        }
        range.removeModifier(key);
        range.addTransientModifier(new AttributeModifier(key, RANGE_PENALTY_SCALAR,
                AttributeModifier.Operation.ADD_SCALAR));
    }

    private void removeRangePenalty() {
        Player player = getPlayer();
        NamespacedKey key = rangeKey();
        if (player == null || key == null) {
            return;
        }
        AttributeInstance range = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (range != null) {
            range.removeModifier(key);
        }
    }

    private NamespacedKey rangeKey() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin == null ? null : new NamespacedKey(plugin, "boxer_range_penalty");
    }
}
