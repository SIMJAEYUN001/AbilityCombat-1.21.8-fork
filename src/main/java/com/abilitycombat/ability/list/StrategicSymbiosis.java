package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

@AbilityManifest(name = "전략적 공생 (StrategicSymbiosis)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 타격 - 전략적 공생]§f §8(쿨타임: 9000초)",
        "§7철괴로 타격한 적 플레이어를 §d전략적 공생 관계§7로 지정합니다.",
        "",
        "§e§l[패시브 - 피해 이전]",
        "§7공생 대상이 받는 최종 피해의 §f10%§7만큼 대신 받습니다.",
        "§7대신 받은 피해는 자신의 방어력과 보호 인챈트를 무시합니다.",
        "§7공생 대상이 입힌 최종 피해의 §f25%§7만큼 체력을 회복합니다.",
        "",
        "§e§l[패시브 - 관계 청산]",
        "§7공생 대상이 사망하면 최대 체력이 §a2§7 증가하고",
        "§7전략적 공생의 쿨타임이 초기화됩니다."
}, summarize = {
        "§7철괴 타격§f: 적 1명을 공생 대상으로 지정",
        "§7대상 피격§f: 최종 피해의 10%만큼 대신 받음",
        "§7대상 공격§f: 대상이 입힌 최종 피해 25% 회복",
        "§7대상 사망§f: 최대 체력 +2, 쿨타임 초기화"
})
public class StrategicSymbiosis extends AbilityBase {

    private static final int COOLDOWN_SECONDS = 9000;
    private static final double SHARED_DAMAGE_RATIO = 0.10;
    private static final double HEAL_RATIO = 0.25;
    private static final double MAX_HEALTH_BONUS = 2.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);

    private UUID partnerId;

    public StrategicSymbiosis(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
        subscribeEvent(PlayerDeathEvent.class);
    }

    @Override
    protected void onDeactivate() {
        partnerId = null;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent damageByEntity) {
            onDamageByEntity(damageByEntity);
        } else if (event instanceof PlayerDeathEvent deathEvent) {
            onPlayerDeath(deathEvent);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (tryDesignatePartner(event)) {
            return;
        }
        Player partner = getPartner();
        if (partner == null) {
            return;
        }
        sharePartnerIncomingDamage(event, partner);
        healFromPartnerOutgoingDamage(event, partner);
    }

    private boolean tryDesignatePartner(EntityDamageByEntityEvent event) {
        Player owner = getPlayer();
        if (!(event.getDamager() instanceof Player attacker) || !attacker.equals(owner)) {
            return false;
        }
        if (attacker.getInventory().getItemInMainHand().getType() != Material.IRON_INGOT) {
            return false;
        }
        if (!(event.getEntity() instanceof Player target) || target.equals(owner)) {
            return false;
        }
        if (!LocationUtil.isValidTarget(owner, target)) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return true;
        }

        partnerId = target.getUniqueId();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        owner.sendMessage(Component.text("§d전략적 공생 §f대상: §e" + target.getName()));
        target.sendMessage(Component.text("§d" + owner.getName() + "§f님과 전략적 공생 관계가 되었습니다."));
        owner.playSound(owner.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        target.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
        return true;
    }

    private void sharePartnerIncomingDamage(EntityDamageByEntityEvent event, Player partner) {
        Entity damaged = event.getEntity();
        if (!damaged.getUniqueId().equals(partner.getUniqueId())) {
            return;
        }
        Player owner = getPlayer();
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            return;
        }
        double finalDamage = getCalculatedFinalDamage(event);
        if (finalDamage <= 0.0) {
            return;
        }
        decreaseIncomingDamage(event, SHARED_DAMAGE_RATIO * 100.0);
        double sharedDamage = finalDamage * SHARED_DAMAGE_RATIO;
        Bukkit.getScheduler().runTask(AbilityCombat.getPlugin(), () -> applyExactHealthLoss(owner, sharedDamage));
    }

    private void healFromPartnerOutgoingDamage(EntityDamageByEntityEvent event, Player partner) {
        if (!(event.getDamager() instanceof Player attacker) || !attacker.getUniqueId().equals(partner.getUniqueId())) {
            return;
        }
        Player owner = getPlayer();
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            return;
        }
        double dealtDamage = getActualDealtDamage(event);
        if (dealtDamage <= 0.0) {
            return;
        }
        double healAmount = dealtDamage * HEAL_RATIO;
        Bukkit.getScheduler().runTask(AbilityCombat.getPlugin(), () -> healOwner(owner, healAmount));
    }

    private double getActualDealtDamage(EntityDamageByEntityEvent event) {
        double finalDamage = getCalculatedFinalDamage(event);
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return finalDamage;
        }
        return Math.min(finalDamage, target.getHealth());
    }

    private void applyExactHealthLoss(Player owner, double amount) {
        if (owner == null || amount <= 0.0 || !owner.isOnline() || owner.isDead()) {
            return;
        }
        owner.setHealth(Math.max(0.0, owner.getHealth() - amount));
        owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 1.4f);
    }

    private void healOwner(Player owner, double amount) {
        if (owner == null || amount <= 0.0 || !owner.isOnline() || owner.isDead()) {
            return;
        }
        AttributeInstance maxHealth = owner.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        double nextHealth = Math.min(max, owner.getHealth() + amount);
        if (nextHealth <= owner.getHealth()) {
            return;
        }
        owner.setHealth(nextHealth);
        owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.35f, 1.6f);
    }

    private void onPlayerDeath(PlayerDeathEvent event) {
        if (partnerId == null || !event.getEntity().getUniqueId().equals(partnerId)) {
            return;
        }
        Player owner = getPlayer();
        partnerId = null;
        resetCooldown();
        if (owner == null || !owner.isOnline()) {
            return;
        }
        increaseMaxHealth(owner, MAX_HEALTH_BONUS);
        owner.sendMessage(Component.text("§d전략적 공생 §f대상이 사망해 최대 체력이 §a2§f 증가했습니다."));
        owner.playSound(owner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }

    private Player getPartner() {
        if (partnerId == null) {
            return null;
        }
        Player partner = Bukkit.getPlayer(partnerId);
        if (partner == null || !partner.isOnline()) {
            return null;
        }
        return partner;
    }

    private void resetCooldown() {
        if (cooldown.isCooldown()) {
            cooldown.stop(true);
        }
        Player owner = getPlayer();
        if (owner != null) {
            owner.setCooldown(Material.IRON_INGOT, 0);
        }
    }

    private void increaseMaxHealth(Player player, double amount) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double nextMax = maxHealth.getBaseValue() + amount;
        maxHealth.setBaseValue(nextMax);
        player.setHealth(Math.min(nextMax, player.getHealth() + amount));
    }
}
