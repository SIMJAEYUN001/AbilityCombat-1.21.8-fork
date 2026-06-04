package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.FireworkEffect;
import org.bukkit.Color;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@AbilityManifest(name = "시간 역행 (TimeRewind)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 시간 역행]§f §8(쿨타임: 50초)",
        "§f5초§7 전의 상태(위치, 체력, 상태효과)로",
        "§7즉시 되돌아갑니다.",
        "",
        "§7역행 후 §f2초§7간 §b무적§7 상태가 됩니다.",
        "",
        "§e§l[패시브 - 자동 발동]",
        "§7죽음에 이르는 피해를 입을 때",
        "§7쿨타임이 남아있다면 §a자동으로 발동§7됩니다."
}, summarize = {
        "§7철괴 우클릭§f: 5초 전 상태 복귀",
        "§7치명상 시§f: 자동 발동"
})
public class TimeRewind extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 50;
    private static final int HISTORY_SECONDS = 5;
    private static final int INVULN_SECONDS = 2;

    private final ActionbarCooldown cooldown = new ActionbarCooldown(COOLDOWN_SECONDS);
    private int remainingInvulnSeconds = 0;
    private final Deque<Snapshot> snapshots = new ArrayDeque<>();
    private boolean rewinding;
    private boolean storedInvulnerable;

    public TimeRewind(Participant participant) {
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
        if (remainingInvulnSeconds > 0) {
            getPlayer().setInvulnerable(storedInvulnerable);
            remainingInvulnSeconds = 0;
        }
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ActiveHandler.ClickType.RIGHT_CLICK) {
            return false;
        }
        return performRewind(true);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent) {
            onDamage((EntityDamageEvent) event);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (rewinding || cooldown.isCooldown()) {
            return;
        }
        double finalDamage = getCalculatedFinalDamage(event);
        if (player.getHealth() - finalDamage <= 0.0) {
            if (performRewind(false)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean performRewind(boolean notifyOnCooldown) {
        if (cooldown.isCooldown()) {
            if (notifyOnCooldown) {
                notifyCooldown(cooldown);
            }
            return false;
        }
        if (rewinding) {
            return false;
        }
        Snapshot snapshot = snapshots.peekFirst();
        if (snapshot == null) {
            return false;
        }
        rewinding = true;
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        restoreSnapshot(snapshot);
        startInvulnerability();
        AbilityCombat.getPlugin().getServer().getScheduler().runTaskLater(AbilityCombat.getPlugin(),
                () -> rewinding = false, 2L);
        return true;
    }

    private void restoreSnapshot(Snapshot snapshot) {
        Player player = getPlayer();
        Location origin = player.getLocation().clone();
        player.teleport(snapshot.location);
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHealth, snapshot.health));
        player.setFoodLevel(snapshot.foodLevel);
        player.setSaturation(snapshot.saturation);
        player.setFireTicks(snapshot.fireTicks);
        player.setAbsorptionAmount(snapshot.absorption);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : snapshot.effects) {
            player.addPotionEffect(effect);
        }
        spawnRewindEffects(origin, snapshot.location);
    }

    private void startInvulnerability() {
        Player player = getPlayer();
        storedInvulnerable = player.isInvulnerable();
        player.setInvulnerable(true);
        remainingInvulnSeconds = INVULN_SECONDS;
        registerTick();
    }

    private void captureSnapshot() {
        Player player = getPlayer();
        Snapshot snapshot = new Snapshot(
                player.getLocation().clone(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getActivePotionEffects().stream().toList(),
                player.getFireTicks(),
                player.getAbsorptionAmount());
        snapshots.addLast(snapshot);
        while (snapshots.size() > HISTORY_SECONDS) {
            snapshots.removeFirst();
        }
    }

    private void spawnRewindEffects(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
            return;
        }
        if (from.getWorld().equals(to.getWorld())) {
            spawnTrail(from, to);
        }
        playRewindSound(to);
        spawnFirework(to);
    }

    private void spawnTrail(Location from, Location to) {
        double distance = from.distance(to);
        if (distance <= 0.0) {
            return;
        }
        int steps = Math.min(40, Math.max(8, (int) (distance / 0.4)));
        double step = 1.0 / steps;
        Vector delta = to.toVector().subtract(from.toVector());
        Vector stepVector = delta.multiply(step);
        Location point = from.clone();
        for (int i = 0; i <= steps; i++) {
            ParticleUtil.spawnParticle(from.getWorld(), Particle.REVERSE_PORTAL, point, 2, 0, 0, 0, 0, 2, 0);
            point.add(stepVector);
        }
    }

    private void playRewindSound(Location location) {
        location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
        location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.2f);
    }

    private void spawnFirework(Location location) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.AQUA, Color.WHITE)
                .withFade(Color.BLUE)
                .flicker(true)
                .trail(true)
                .build());
        meta.setPower(0);
        firework.setFireworkMeta(meta);
        firework.detonate();
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed()) {
            unregisterTick();
            return;
        }

        // 1초마다 실행 (20틱)
        if (tick % 20 == 0) {
            // Snapshot Capture
            captureSnapshot();

            // Invulnerability Duration
            if (remainingInvulnSeconds > 0) {
                remainingInvulnSeconds--;
                if (remainingInvulnSeconds <= 0) {
                    getPlayer().setInvulnerable(storedInvulnerable);
                }
            }
        }
    }

    private static class Snapshot {
        private final Location location;
        private final double health;
        private final int foodLevel;
        private final float saturation;
        private final List<PotionEffect> effects;
        private final int fireTicks;
        private final double absorption;

        private Snapshot(Location location, double health, int foodLevel, float saturation, List<PotionEffect> effects,
                int fireTicks, double absorption) {
            this.location = location;
            this.health = health;
            this.foodLevel = foodLevel;
            this.saturation = saturation;
            this.effects = effects;
            this.fireTicks = fireTicks;
            this.absorption = absorption;
        }
    }
}
