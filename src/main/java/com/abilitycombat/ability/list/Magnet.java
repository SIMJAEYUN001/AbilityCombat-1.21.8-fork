package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
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
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Collection;

@AbilityManifest(name = "마그넷 (Magnet)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 마그네틱 코어]§f §8(쿨타임: 30초)",
        "§7전방에 §6마그네틱 코어§7를 생성합니다. (지속: §f8초§7)",
        "",
        "§7코어는 주변 §f8칸§7 이내의 모든 §e플레이어§7를",
        "§7천천히 끌어당깁니다. (본인 제외)",
        "",
        "§7지속시간 종료 시 코어가 §c폭발§7하여",
        "§f6칸§7 이내의 플레이어에게 §c20 데미지§7를 입힙니다."
}, summarize = {
        "§7철괴 우클릭§f: 코어 생성 → 플레이어 흡인 → 폭발"
})
public class Magnet extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 30;
    private static final int DURATION_SECONDS = 8;
    private static final double PULL_RANGE = 8.0;
    private static final double EXPLOSION_RANGE = 6.0;
    private static final double EXPLOSION_DAMAGE = 20.0;
    private static final double PULL_FORCE = 0.15;
    private static final int TICK_PERIOD = 2;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int remainingCoreTicks = 0;
    private ArmorStand core;

    public Magnet(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        // No events used
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        removeCore();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (isCoreActive()) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        startCore();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    protected void onDestroy() {
        removeCore();
    }

    private void spawnCore() {
        removeCore();
        Location location = getPlayer().getLocation().clone();
        Location spawn = location.add(location.getDirection().normalize().multiply(2.0));
        World world = spawn.getWorld();
        if (world == null) {
            return;
        }
        core = world.spawn(spawn, ArmorStand.class);
        core.setInvisible(true);
        core.setGravity(false);
        core.setMarker(true);
        core.setBasePlate(false);
        core.setSmall(true);
        core.setInvulnerable(true);
        core.getEquipment().setHelmet(new ItemStack(Material.IRON_BLOCK));
        AbilityCombat.markAbilityArmorStand(core);

        world.playSound(spawn, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }

    private void removeCore() {
        if (core != null && !core.isDead()) {
            core.remove();
        }
        core = null;
    }

    private void pullPlayers(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        Collection<LivingEntity> nearby = LocationUtil.getNearbyLivingEntities(center, PULL_RANGE,
                e -> e instanceof Player && !e.equals(getPlayer()) && LocationUtil.isValidTarget(getPlayer(), e));

        for (LivingEntity entity : nearby) {
            Vector pull = center.toVector().subtract(entity.getLocation().toVector());
            double distance = pull.length();
            if (distance < 1.0) {
                continue;
            }
            Vector velocity = pull.normalize().multiply(PULL_FORCE);
            entity.setVelocity(entity.getVelocity().add(velocity));
        }

        // 파티클 효과
        ParticleUtil.spawnParticle(world, Particle.ELECTRIC_SPARK, center, 5, 0.5, 0.5, 0.5, 0.02, 2, 0);
    }

    private void explodeCore() {
        if (core == null || core.isDead()) {
            return;
        }
        Location location = core.getLocation();
        World world = location.getWorld();
        removeCore();

        if (world == null) {
            return;
        }

        // 폭발 이펙트와 소리
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
        ParticleUtil.spawnParticle(world, Particle.EXPLOSION, location, 3, 0.5, 0.5, 0.5, 0, 1, 0);
        ParticleUtil.spawnParticle(world, Particle.ELECTRIC_SPARK, location, 50, 2, 2, 2, 0.1, 1, 0);

        // 6칸 내 플레이어에게 20 데미지
        Collection<LivingEntity> victims = LocationUtil.getNearbyLivingEntities(location, EXPLOSION_RANGE,
                e -> e instanceof Player && !e.equals(getPlayer()) && LocationUtil.isValidTarget(getPlayer(), e));

        for (LivingEntity victim : victims) {
            victim.damage(EXPLOSION_DAMAGE, getPlayer());
        }
    }

    private void startCore() {
        remainingCoreTicks = DURATION_SECONDS * (20 / TICK_PERIOD);
        spawnCore();
    }

    private void stopCore(boolean explode) {
        if (explode) {
            explodeCore();
        } else {
            removeCore();
        }
        remainingCoreTicks = 0;
    }

    private boolean isCoreActive() {
        return remainingCoreTicks > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % TICK_PERIOD == 0) {
            if (isCoreActive()) {
                if (core == null || core.isDead()) {
                    stopCore(false);
                    return;
                }
                pullPlayers(core.getLocation());
                remainingCoreTicks--;
                if (remainingCoreTicks <= 0) {
                    stopCore(true);
                }
            }
        }
    }
}
