package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.ParticleUtil;
import com.abilitycombat.vfx.Circle;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.attribute.Attribute;
import org.bukkit.Color;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Set;

@AbilityManifest(name = "버서커 (Berserker)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 불굴의 의지]§f §8(쿨타임: 45초)",
        "§f8초§7간 이동 방해 상태이상을 해제하고",
        "§b이동 속도 증가§7 및 §c근접 공격력 증가§7 효과를 얻습니다.",
        "§7지속 중에는 체력이 §c반 칸(1HP)§7 아래로 내려가지 않습니다.",
        "",
        "§e§l[패시브 - 전사의 피]",
        "§7원거리/투사체 공격의 피해가 §f50%§7 감소합니다.",
        "§f5칸§7 밖에서 오는 공격 피해가 추가로 §f25%§7 감소합니다.",
        "§7잃은 체력에 비례해 근접 피해가 최대 §c1.35배§7까지 증가합니다."
}, summarize = {
        "§7철괴 우클릭§f: 8초 광폭화 (체력 1 유지)",
        "§7패시브§f: 원거리 피해 감소"
})
public class Berserker extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 45;
    private static final int RAGE_SECONDS = 8;
    private static final DustOptions RAGE_DUST = new DustOptions(Color.fromRGB(255, 60, 60), 0.8f);
    private static final Set<PotionEffectType> NEGATIVE_EFFECTS = Set.of(
            PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE,
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.WEAKNESS,
            PotionEffectType.HUNGER,
            PotionEffectType.BLINDNESS,
            PotionEffectType.NAUSEA);

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int remainingRageSeconds = 0;
    private static final List<Vector> RAGE_CIRCLE_VECTORS = Circle.of(3.5, 20);

    public Berserker(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        clearRageBossBar();
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
        if (isRageActive()) {
            return false;
        }
        Player player = getPlayer();
        removeNegativeEffects(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, RAGE_SECONDS * 20, 1, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, RAGE_SECONDS * 20, 0, true, false));
        startRage();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent) {
            onDamage((EntityDamageEvent) event);
        } else if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!event.getEntity().equals(getPlayer())) {
            return;
        }
        if (isRageActive()) {
            double remaining = getPlayer().getHealth() - getCalculatedFinalDamage(event);
            if (remaining < 1.0) {
                event.setDamage(Math.max(0, getPlayer().getHealth() - 1.0));
            }
            spawnShieldParticles();
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity().equals(getPlayer())) {
            Entity damager = event.getDamager();
            if (damager instanceof Projectile) {
                decreaseIncomingDamage(event, 50.0);
            }
            if (damager instanceof Entity) {
                double distance = damager.getLocation().distance(getPlayer().getLocation());
                if (distance > 5.0) {
                    decreaseIncomingDamage(event, 75.0);
                }
            }
        }
        if (event.getDamager().equals(getPlayer())) {
            double maxHealth = getPlayer().getAttribute(Attribute.MAX_HEALTH).getValue();
            double missing = Math.max(0.0, maxHealth - getPlayer().getHealth());
            double ratio = missing / maxHealth;
            increaseOutgoingDamage(event, 35.0 * ratio);
        }
    }

    private void removeNegativeEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (NEGATIVE_EFFECTS.contains(effect.getType())) {
                player.removePotionEffect(effect.getType());
            }
        }
    }

    private void startRage() {
        remainingRageSeconds = RAGE_SECONDS;
        registerTick();
    }

    private boolean isRageActive() {
        return remainingRageSeconds > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            if (isRageActive()) {
                if (getPlayer().getHealth() < 1.0) {
                    getPlayer().setHealth(1.0);
                }
                spawnRageCircle();
                updateRageBossBar(remainingRageSeconds);
                remainingRageSeconds--;
                if (remainingRageSeconds <= 0) {
                    clearRageBossBar();
                }
            }
        }
    }

    private void updateRageBossBar(int remaining) {
        var manager = getBossBarManager();
        if (manager == null) {
            return;
        }
        float progress = remaining / (float) RAGE_SECONDS;
        Component title = Component.text("광폭화 " + remaining + "초", NamedTextColor.RED);
        manager.update(getPlayer(), "berserker:rage", 7, title, progress, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    }

    private void clearRageBossBar() {
        var manager = getBossBarManager();
        if (manager != null) {
            manager.clear(getPlayer(), "berserker:rage");
        }
    }

    private void spawnRageCircle() {
        for (Vector offset : RAGE_CIRCLE_VECTORS) {
            var loc = com.abilitycombat.utils.LocationPool.get(getPlayer().getWorld(), 0, 0, 0);
            loc.setX(getPlayer().getLocation().getX() + offset.getX());
            loc.setY(getPlayer().getLocation().getY() + 0.2);
            loc.setZ(getPlayer().getLocation().getZ() + offset.getZ());

            ParticleUtil.spawnParticle(
                    getPlayer().getWorld(),
                    Particle.DUST,
                    loc,
                    1,
                    0,
                    0,
                    0,
                    0,
                    RAGE_DUST,
                    2,
                    0);
        }
    }

    private void spawnShieldParticles() {
        var loc = com.abilitycombat.utils.LocationPool.get(getPlayer().getWorld(), 0, 0, 0);
        loc.setX(getPlayer().getLocation().getX());
        loc.setY(getPlayer().getLocation().getY() + 1.0);
        loc.setZ(getPlayer().getLocation().getZ());

        ParticleUtil.spawnParticle(
                getPlayer().getWorld(),
                Particle.DUST,
                loc,
                10,
                0.6,
                0.6,
                0.6,
                0,
                RAGE_DUST,
                2,
                0);
    }
}
