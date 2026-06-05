package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.DamageModifier;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;

@AbilityManifest(name = "스토커 (Stalker)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 집착]",
        "§7같은 대상을 공격할 때마다 §6집착 스택§7이 쌓입니다 (최대 §f5§7, §f10초§7 유지)",
        "§75스택이 되면 스택을 소모해 §8실명 2초§7와 §c받는 피해 +16%§7를 부여합니다",
        "§7피해 증가 디버프는 §f20초§7간 유지됩니다",
        "",
        "§e§l[철괴 우클릭 - 관전]§f §8(쿨타임: 105초 - 스택×10, 최소 30초)",
        "§7§f20칸§7 내 대상에게 §b무적/투명§7 상태로 §f3초§7간 돌진합니다",
        "",
        "§e§l[검 우클릭 - 질주]§f §8(쿨타임: 4초)",
        "§7§f6칸§7 내 대상에게 짧게 돌진합니다"
}, summarize = {
        "§7집착 스택§f: 5스택 소모 시 실명 2초",
        "§7디버프§f: 받는 피해 +16% 20초",
        "§7철괴/검 우클릭§f: 돌진"
})
public class Stalker extends AbilityBase implements ActiveHandler {

    private static final int IRON_BASE_COOLDOWN = 105;
    private static final int SWORD_COOLDOWN = 4;
    private static final double LOOK_RANGE = 20.0;
    private static final double SHORT_RANGE = 6.0;
    private static final int DASH_SECONDS = 3;
    private static final int MAX_OBSESSION_STACK = 5;
    private static final int OBSESSION_BLIND_TICKS = 40;
    private static final int VULNERABILITY_TICKS = 400;
    private static final double VULNERABILITY_PERCENT = 16.0;
    private static final String VULNERABILITY_SOURCE_KEY = "stalker_obsession";

    private final Cooldown swordCooldown = new Cooldown(SWORD_COOLDOWN);

    private Cooldown ironCooldown;
    private LivingEntity dashTarget;
    private boolean dashing;
    private int remainingDashSeconds = 0;
    private int remainingStackSeconds = 0;
    private boolean storedInvulnerable;
    private UUID lastTarget;
    private int stack;

    public Stalker(Participant participant) {
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
        if (dashing) {
            endGhostMode();
        }
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (material == Material.IRON_INGOT) {
            return castIronDash();
        }
        if (isSword(material)) {
            return castSwordDash();
        }
        return false;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getDamager() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!LocationUtil.isValidTarget(player, target)) {
            return;
        }
        if (lastTarget == null || !lastTarget.equals(target.getUniqueId())) {
            lastTarget = target.getUniqueId();
            stack = 0;
        }
        stack = Math.min(MAX_OBSESSION_STACK, stack + 1);
        if (stack >= MAX_OBSESSION_STACK) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, OBSESSION_BLIND_TICKS, 0, true, false));
            DamageModifier.applyIncoming(target, VULNERABILITY_TICKS, VULNERABILITY_SOURCE_KEY,
                    VULNERABILITY_PERCENT);
            stack = 0;
            lastTarget = null;
            remainingStackSeconds = 0;
            return;
        }

        // 스택 리셋 타이머 갱신
        remainingStackSeconds = 10;
        registerTick();
    }

    private boolean castIronDash() {
        if (dashing) {
            return false;
        }
        if (ironCooldown != null && ironCooldown.isCooldown()) {
            notifyCooldown(ironCooldown);
            return false;
        }
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, getPlayer(), LOOK_RANGE,
                entity -> LocationUtil.isValidTarget(getPlayer(), entity));
        if (target == null) {
            return false;
        }
        dashTarget = target;
        dashing = true;
        startGhostMode();
        remainingDashSeconds = DASH_SECONDS;
        registerTick();
        int cooldownSeconds = Math.max(30, IRON_BASE_COOLDOWN - stack * 10);
        ironCooldown = new Cooldown(cooldownSeconds);
        ironCooldown.start();
        applyIronCooldownIfEmpty(cooldownSeconds);
        return true;
    }

    private boolean castSwordDash() {
        if (swordCooldown.isCooldown()) {
            return false;
        }
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, getPlayer(), SHORT_RANGE,
                entity -> LocationUtil.isValidTarget(getPlayer(), entity));
        if (target == null) {
            return false;
        }
        Vector velocity = target.getLocation().toVector().subtract(getPlayer().getLocation().toVector()).normalize()
                .multiply(1.4);
        velocity.setY(0.2);
        getPlayer().setVelocity(velocity);
        swordCooldown.start();
        return true;
    }

    private void startGhostMode() {
        Player player = getPlayer();
        storedInvulnerable = player.isInvulnerable();
        player.setInvulnerable(true);
        player.setInvisible(true);
        player.setCollidable(false);
    }

    private void endGhostMode() {
        Player player = getPlayer();
        player.setInvulnerable(storedInvulnerable);
        player.setInvisible(false);
        player.setCollidable(true);
        dashing = false;
        dashTarget = null;
    }

    private boolean isSword(Material material) {
        return material.name().endsWith("_SWORD");
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed()) {
            unregisterTick();
            return;
        }

        // 1초마다 실행 (20틱)
        if (tick % 20 == 0) {
            boolean active = false;

            // Dash Logic
            if (dashing && remainingDashSeconds > 0) {
                processDash();
                remainingDashSeconds--;
                if (remainingDashSeconds <= 0 || !dashing) {
                    endGhostMode();
                } else {
                    active = true;
                }
            }

            // Stack Reset Logic
            if (remainingStackSeconds > 0) {
                remainingStackSeconds--;
                if (remainingStackSeconds <= 0) {
                    stack = 0;
                    lastTarget = null;
                } else {
                    active = true;
                }
            }

            if (!active && !dashing) {
                unregisterTick();
            }
        }
    }

    private void processDash() {
        if (dashTarget == null || dashTarget.isDead()) {
            endGhostMode();
            return;
        }
        Player player = getPlayer();
        Vector direction = dashTarget.getLocation().toVector().subtract(player.getLocation().toVector());
        if (direction.lengthSquared() <= 4.0) {
            endGhostMode();
            return;
        }
        Vector velocity = direction.normalize().multiply(1.6);
        velocity.setY(0.2);
        player.setVelocity(velocity);
    }
}
