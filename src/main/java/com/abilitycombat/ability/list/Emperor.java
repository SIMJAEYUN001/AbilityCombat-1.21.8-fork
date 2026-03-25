package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

@AbilityManifest(name = "황제 (Emperor)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 근위병 소환]§f §8(쿨타임: 60초)",
        "§7폭발과 함께 주변 적을 §6밀쳐내고§7",
        "§f2명§7의 §e근위병(스켈레톤)§7을 소환합니다.",
        "",
        "§7근위병은 §b저항 I§7, §f신속 II§7 버프를 가지며",
        "§7최대 §f15초§7간 지속됩니다.",
        "",
        "§7근위병은 주변 적을 자동으로 추격합니다.",
        "§7자신이 공격받으면 근위병의 타겟이 변경됩니다."
}, summarize = {
        "§7철괴 우클릭§f: 폭발 + 근위병 2마리 소환 (15초)"
})
public class Emperor extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 60;
    private static final int DURATION_SECONDS = 15;
    private static final int GUARD_COUNT = 2;
    private static final double KNOCKBACK_RADIUS = 5.0;
    private static final String KEY_GUARD = "emperor_guard";

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final List<Skeleton> guards = new ArrayList<>();
    private int remainingSummonSeconds = 0;

    public Emperor(Participant participant) {
        super(participant);
    }

    public static NamespacedKey getGuardKey(AbilityCombat plugin) {
        return new NamespacedKey(plugin, KEY_GUARD);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityTargetLivingEntityEvent.class);
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        clearGuards();
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ActiveHandler.ClickType.RIGHT_CLICK) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }

        // 폭발 이펙트 및 주변 밀쳐내기
        triggerExplosion();

        // 근위병 소환
        summonGuards();
        startSummon();

        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityTargetLivingEntityEvent) {
            onTarget((EntityTargetLivingEntityEvent) event);
        } else if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Skeleton skeleton)) {
            return;
        }
        if (event.getTarget() == null) {
            return;
        }
        if (event.getTarget().equals(getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (isGuard(skeleton)) {
            if (event.getTarget() instanceof LivingEntity living
                    && !com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), living)) {
                event.setCancelled(true);
            }
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        // 플레이어가 공격받으면 근위병 타겟 변경
        if (event.getEntity().equals(getPlayer()) && event.getDamager() instanceof LivingEntity damager) {
            if (com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), damager)) {
                retargetGuards(damager);
            }
        }
    }

    @Override
    protected void onDestroy() {
        clearGuards();
    }

    private void triggerExplosion() {
        Player player = getPlayer();
        Location center = player.getLocation();
        World world = player.getWorld();

        // 폭발 이펙트
        world.spawnParticle(Particle.EXPLOSION, center, 1);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        // 주변 플레이어 밀쳐내기
        for (LivingEntity entity : com.abilitycombat.utils.LocationUtil.getNearbyLivingEntities(
                center, KNOCKBACK_RADIUS, player, e -> !e.equals(player))) {
            Vector knockback = entity.getLocation().toVector().subtract(center.toVector()).normalize().multiply(2.0);
            knockback.setY(0.3);
            entity.setVelocity(knockback);
        }
    }

    private void summonGuards() {
        clearGuards();
        World world = getPlayer().getWorld();
        Location playerLoc = getPlayer().getLocation();

        for (int i = 0; i < GUARD_COUNT; i++) {
            // 플레이어 양 옆에 소환
            double angle = (i == 0) ? Math.toRadians(90) : Math.toRadians(-90);
            double offsetX = Math.cos(playerLoc.getYaw() * Math.PI / 180 + angle) * 1.5;
            double offsetZ = Math.sin(playerLoc.getYaw() * Math.PI / 180 + angle) * 1.5;
            Location spawnLoc = playerLoc.clone().add(offsetX, 0, offsetZ);

            Skeleton skeleton = world.spawn(spawnLoc, Skeleton.class);
            skeleton.customName(Component.text("근위병", NamedTextColor.GOLD));
            skeleton.setCustomNameVisible(false);
            skeleton.setInvulnerable(false); // 무적 아님

            // 장비: 금 투구, 금 칼 (나머지 제거)
            if (skeleton.getEquipment() != null) {
                skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
                skeleton.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                skeleton.getEquipment().setChestplate(null);
                skeleton.getEquipment().setLeggings(null);
                skeleton.getEquipment().setBoots(null);
            }

            // 버프: 저항1, 신속2 (힘 삭제)
            int buffDuration = DURATION_SECONDS * 20;
            skeleton.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, buffDuration, 0, true, false));
            skeleton.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, buffDuration, 1, true, false));

            // PDC 태그
            skeleton.getPersistentDataContainer().set(
                    getGuardKey(AbilityCombat.getPlugin()),
                    PersistentDataType.BYTE, (byte) 1);

            guards.add(skeleton);
        }

        // 가장 가까운 적 타겟팅
        LivingEntity target = getNearestTarget();
        if (target != null) {
            retargetGuards(target);
        }
    }

    private LivingEntity getNearestTarget() {
        Player player = getPlayer();
        return LocationUtil.getNearestEntity(LivingEntity.class, player.getLocation(), 16.0,
                entity -> !(entity instanceof Skeleton skeleton && isGuard(skeleton))
                        && LocationUtil.isValidTarget(getPlayer(), entity));
    }

    private void retargetGuards(LivingEntity target) {
        for (Skeleton skeleton : guards) {
            if (skeleton.isDead()) {
                continue;
            }
            skeleton.setTarget(target);
        }
    }

    private void clearGuards() {
        for (Skeleton skeleton : guards) {
            if (!skeleton.isDead()) {
                skeleton.remove();
            }
        }
        guards.clear();
        remainingSummonSeconds = 0;
    }

    private boolean isGuard(Skeleton skeleton) {
        return skeleton.getPersistentDataContainer().has(
                getGuardKey(AbilityCombat.getPlugin()),
                PersistentDataType.BYTE);
    }

    private void startSummon() {
        remainingSummonSeconds = DURATION_SECONDS;
        registerTick();
    }

    private void stopSummon() {
        clearGuards();
        remainingSummonSeconds = 0;
    }

    private boolean isSummoned() {
        return remainingSummonSeconds > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            if (isSummoned()) {
                remainingSummonSeconds--;
                if (remainingSummonSeconds <= 0) {
                    stopSummon();
                }
            }
        }
    }
}
