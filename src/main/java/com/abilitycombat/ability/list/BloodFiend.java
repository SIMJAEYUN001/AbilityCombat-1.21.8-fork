package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.ParticleUtil;
import com.abilitycombat.utils.LocationUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

@AbilityManifest(name = "흡혈마 (BloodFiend)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.UNDEAD, explain = {
        "§e§l[패시브 - 흡혈마]",
        "§7공격 게이지가 가득 찬 상태로 §f플레이어§7를 타격하면",
        "§c흡혈 스택§7을 §f1§7 얻습니다. (최대 §f4§7스택, §f10초§7 유지)",
        "",
        "§7스택 1개당 타격 시 §c추가 피해 +0.5§7 (최대 §c+2§7)",
        "§7§c4스택§7 보유 시 타격하면 §a체력 반 칸 회복§7",
        "",
        "§e§l[철괴 우클릭 - 강탈]§f §8(쿨타임: 50초)",
        "§7스택을 모두 소모하고 §f4칸§7 내 대상에게",
        "§c2 + 스택당 1§7의 §c고정 피해§7를 입히고 §a입힌 데미지에 비례해서 회복§7합니다.",
        "§8쿨타임 중에는 스택으로 인한 추가 피해가 적용되지 않습니다."
}, summarize = {
        "§7패시브§f: 스택당 추가 피해 +0.5, 4스택 시 회복",
        "§7철괴 우클릭§f: 스택 소모 + 체력 강탈"
})
public class BloodFiend extends AbilityBase implements ActiveHandler {

    private static final int MAX_STACKS = 4;
    private static final double BONUS_DAMAGE_PER_STACK = 0.5;
    private static final float COOLDOWN_THRESHOLD = 0.99f;
    private static final int STACK_RESET_SECONDS = 10;
    private static final int STACK_RESET_TICKS = STACK_RESET_SECONDS * 20;
    private static final int HUD_PRIORITY = 2;
    private static final long HUD_PERIOD_TICKS = 20L;
    private static final String HUD_KEY = "bloodfiend:stacks";
    private static final int ACTIVE_COOLDOWN_SECONDS = 50;
    private static final double ACTIVE_RANGE = 4.0;
    private static final double BASE_STEAL_DAMAGE = 2.0;

    private int stacks;
    private int stackResetTicks;
    private final Cooldown activeCooldown = new Cooldown(ACTIVE_COOLDOWN_SECONDS);
    private final BossBarGauge durationGauge = new BossBarGauge("duration", 5, BossBar.Color.RED,
            BossBar.Overlay.PROGRESS);

    public BloodFiend(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        clearHud();
        durationGauge.clear();
    }

    @Override
    protected void onDestroy() {
        clearHud();
        durationGauge.clear();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (activeCooldown.isCooldown()) {
            notifyCooldown(activeCooldown);
            return false;
        }

        Player player = getPlayer();
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, player, ACTIVE_RANGE,
                LocationUtil.withValidTarget(getPlayer(), entity -> !entity.equals(player)));
        if (target == null) {
            return false;
        }

        // 강탈 데미지 계산: 2 + 스택당 1
        double stealAmount = BASE_STEAL_DAMAGE + stacks;

        // 스택 소모
        stacks = 0;
        updateHud();

        // 고정 데미지 적용
        double newTargetHealth = target.getHealth() - stealAmount;
        if (newTargetHealth <= 0) {
            target.setHealth(0.01);
            target.damage(0.01, player);
        } else {
            target.setHealth(newTargetHealth);
            target.damage(0.01, player);
            target.setNoDamageTicks(0);
        }

        // 사용자 회복
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = attribute != null ? attribute.getValue() : 20.0;
        double newHealth = Math.min(maxHealth, player.getHealth() + stealAmount);
        player.setHealth(newHealth);

        // 효과
        spawnBloodEffect(target);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.8f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);

        activeCooldown.start();
        applyIronCooldownIfEmpty(ACTIVE_COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker) || !attacker.equals(getPlayer())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        // 쿨타임 중에는 스택 추가 피해 없음
        if (!activeCooldown.isCooldown()) {
            double bonusDamage = stacks * BONUS_DAMAGE_PER_STACK;
            if (bonusDamage > 0) {
                addOutgoingDamage(event, bonusDamage);
            }
        }

        if (stacks >= MAX_STACKS && target instanceof Player) {
            healHalfHeart(attacker);
            spawnBloodEffect(target);
        }

        if (target instanceof Player) {
            refreshStackTimer();
            if (getAttackCooldown(attacker) >= COOLDOWN_THRESHOLD) {
                stacks = Math.min(MAX_STACKS, stacks + 1);
            }
            updateHud();
        }
    }

    private float getAttackCooldown(Player player) {
        return player.getCooledAttackStrength(0.0f);
    }

    private void healHalfHeart(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = attribute != null ? attribute.getValue() : 20.0;
        double newHealth = Math.min(maxHealth, player.getHealth() + 1.0);
        player.setHealth(newHealth);
    }

    private void refreshStackTimer() {
        stackResetTicks = STACK_RESET_TICKS;
    }

    private void updateHud() {
        var channel = getActionbarChannel();
        Component stackMessage = Component.text("흡혈 ", NamedTextColor.DARK_RED)
                .append(Component.text(stacks + "/" + MAX_STACKS, NamedTextColor.WHITE));
        if (channel != null) {
            channel.update(getPlayer(), HUD_KEY, HUD_PRIORITY, stackMessage);
        } else {
            getPlayer().sendActionBar(stackMessage);
        }

        if (stacks > 0 && stackResetTicks > 0) {
            double progress = (double) stackResetTicks / STACK_RESET_TICKS;
            int seconds = (int) Math.ceil(stackResetTicks / 20.0);
            Component title = Component.text("유지시간 " + seconds + "초", NamedTextColor.RED);
            durationGauge.update(title, progress);
        } else {
            durationGauge.clear();
        }
    }

    private void spawnBloodEffect(LivingEntity target) {
        if (target == null || target.getWorld() == null) {
            return;
        }
        ParticleUtil.spawnParticle(
                target.getWorld(),
                org.bukkit.Particle.BLOCK,
                target.getLocation().add(0, 1.0, 0),
                12,
                0.4,
                0.6,
                0.4,
                0.1,
                org.bukkit.Material.REDSTONE_BLOCK.createBlockData(),
                1,
                0);
    }

    private void clearHud() {
        var channel = getActionbarChannel();
        if (channel != null) {
            channel.clear(getPlayer(), HUD_KEY);
        } else {
            getPlayer().sendActionBar(Component.empty());
        }
    }

    @Override
    public void onTick(int tick) {
        if (tick % HUD_PERIOD_TICKS == 0) {
            if (stacks > 0) {
                if (stackResetTicks > 0) {
                    stackResetTicks = Math.max(0, stackResetTicks - (int) HUD_PERIOD_TICKS);
                }
                if (stackResetTicks == 0) {
                    stacks = 0;
                }
            }
            updateHud();
        }
    }
}
