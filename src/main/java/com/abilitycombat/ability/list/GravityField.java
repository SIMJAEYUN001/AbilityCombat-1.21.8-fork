package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "중력장 (GravityField)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 중력장]§f §8(쿨타임: 25초)",
        "§7주변 §f8칸§7 이내의 플레이어를 모두 §b높게 띄웁니다§7.",
        "§7띄워진 대상은 §f2초§7 뒤부터 §c10배 중력§7의 영향을 받습니다.",
        "§7착지하면 중력은 정상으로 돌아옵니다.",
        "",
        "§7띄운 대상이 §c낙하 피해§7를 받으면,",
        "§7받은 피해량의 §f50%§7만큼 체력을 §a회복§7합니다."
}, summarize = {
        "§7철괴 우클릭§f: 주변 적 공중 발사",
        "§72초 후§f: 10배 중력 적용",
        "§7낙하 피해 발생 시§f: 피해량 50% 회복"
})
public class GravityField extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 25;
    private static final double RANGE = 8.0;
    private static final double LAUNCH_VELOCITY = 3;
    private static final int GRAVITY_DELAY_TICKS = 20;
    private static final double HEAVY_GRAVITY_MULTIPLIER = 10.0;
    private static final double FALL_DAMAGE_HEAL_RATIO = 0.5;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final Map<UUID, LiftState> liftedTargets = new HashMap<>();

    public GravityField(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        restoreAllGravity();
        liftedTargets.clear();
    }

    @Override
    protected void onDestroy() {
        restoreAllGravity();
        liftedTargets.clear();
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

        Collection<Player> targets = LocationUtil.getNearbyPlayers(getPlayer().getLocation(), RANGE, getPlayer(),
                target -> !target.equals(getPlayer()));
        if (targets.isEmpty()) {
            return false;
        }

        activateGravityField(targets);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent e) {
            onEntityDamage(e);
        }
    }

    private void activateGravityField(Collection<Player> targets) {
        Player caster = getPlayer();
        Location center = caster.getLocation();
        caster.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.9f);
        ParticleUtil.spawnParticle(caster.getWorld(), Particle.ENCHANT, center.clone().add(0, 1, 0),
                40, 0.8, 0.6, 0.8, 0.3, 1, 0);

        for (Player target : targets) {
            liftedTargets.put(target.getUniqueId(), new LiftState(GRAVITY_DELAY_TICKS));
            target.setFallDistance(0);
            target.setVelocity(new Vector(0, LAUNCH_VELOCITY, 0));
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.9f, 0.7f);
            ParticleUtil.spawnParticle(target.getWorld(), Particle.CLOUD, target.getLocation().clone().add(0, 0.2, 0),
                    20, 0.35, 0.15, 0.35, 0.08, 1, 0);
        }
    }

    private void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        LiftState state = liftedTargets.get(target.getUniqueId());
        if (state == null) {
            return;
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        liftedTargets.remove(target.getUniqueId());
        restoreGravity(target, state);
        if (!event.isCancelled()) {
            healCaster(getCalculatedFinalDamage(event) * FALL_DAMAGE_HEAL_RATIO);
        }
    }

    private void healCaster(double amount) {
        if (amount <= 0) {
            return;
        }
        Player caster = getPlayer();
        if (caster == null || caster.isDead()) {
            return;
        }
        AttributeInstance maxHealth = caster.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        caster.setHealth(Math.min(max, caster.getHealth() + amount));
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
    }

    @Override
    public void onTick(int tick) {
        if (liftedTargets.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, LiftState>> iterator = liftedTargets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, LiftState> entry = iterator.next();
            Player target = getPlayer().getServer().getPlayer(entry.getKey());
            if (target == null || !target.isOnline() || target.isDead()) {
                iterator.remove();
                continue;
            }

            LiftState state = entry.getValue();
            if (hasLanded(target)) {
                restoreGravity(target, state);
                iterator.remove();
                continue;
            }

            if (!state.heavyGravity) {
                state.delayTicks--;
                if (state.delayTicks <= 0) {
                    state.heavyGravity = applyHeavyGravity(target);
                }
                continue;
            }

            if (tick % 2 == 0) {
                ParticleUtil.spawnParticle(target.getWorld(), Particle.REVERSE_PORTAL,
                        target.getLocation().clone().add(0, 0.8, 0),
                        6, 0.18, 0.35, 0.18, 0.01, 1, 0);
            }
        }
    }

    private boolean hasLanded(Player target) {
        return target.isOnGround() && target.getVelocity().getY() <= 0.0;
    }

    private boolean applyHeavyGravity(Player target) {
        AttributeInstance gravity = target.getAttribute(Attribute.GRAVITY);
        if (gravity == null) {
            return false;
        }
        gravity.setBaseValue(gravity.getDefaultValue() * HEAVY_GRAVITY_MULTIPLIER);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.8f);
        ParticleUtil.spawnParticle(target.getWorld(), Particle.PORTAL,
                target.getLocation().clone().add(0, 1, 0),
                18, 0.35, 0.5, 0.35, 0.15, 1, 0);
        return true;
    }

    private void restoreGravity(Player target, LiftState state) {
        if (target == null || state == null || !state.heavyGravity) {
            return;
        }
        AttributeInstance gravity = target.getAttribute(Attribute.GRAVITY);
        if (gravity != null) {
            gravity.setBaseValue(gravity.getDefaultValue());
        }
        state.heavyGravity = false;
    }

    private void restoreAllGravity() {
        for (Map.Entry<UUID, LiftState> entry : liftedTargets.entrySet()) {
            Player target = getPlayer().getServer().getPlayer(entry.getKey());
            if (target != null) {
                restoreGravity(target, entry.getValue());
            }
        }
    }

    private static final class LiftState {
        private int delayTicks;
        private boolean heavyGravity;

        private LiftState(int delayTicks) {
            this.delayTicks = delayTicks;
        }
    }
}
