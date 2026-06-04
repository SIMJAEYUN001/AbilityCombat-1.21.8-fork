package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

@AbilityManifest(name = "지금의 일은 나중의 나에게 (Lazyness)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 나태]",
        "§7피해와 회복을 §f3초§7 뒤로 미룹니다.",
        "§7받는 피해가 항상 §c10% 감소§7합니다.",
        "",
        "§e§l[철괴 우클릭 - 지불]§f §8(쿨타임: 30초)",
        "§7미뤄진 피해를 즉시 §c0.5배§7로 줄여서 받습니다."
}, summarize = {
        "§7패시브§f: 피해/회복 3초 지연, 받는 피해 10% 감소",
        "§7철괴 우클릭§f: 피해 0.5배로 즉시 적용"
})
public class Lazyness extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 30;
    private static final int DELAY_SECONDS = 3;
    private static final double MAX_PENDING_DAMAGE = 40.0; // HUD 게이지 최대값
    private static final double PASSIVE_DAMAGE_MULTIPLIER = 0.9;
    private static final double PAY_DAMAGE_MULTIPLIER = 0.5;
    private static final double MINIMAL_DAMAGE = 0.001;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final BossBarGauge pendingGauge = new BossBarGauge("pending", 10, BossBar.Color.RED,
            BossBar.Overlay.PROGRESS);
    private final List<DeferredAction> deferredActions = new ArrayList<>();
    private double pendingDamage;
    private boolean applyingDamage = false; // 재귀 호출 방지 플래그

    public Lazyness(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        updateHud();
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
        subscribeEvent(EntityRegainHealthEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        clearPendingDamage();
        pendingGauge.clear();
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
        if (pendingDamage > 0) {
            applyDamage(pendingDamage * PAY_DAMAGE_MULTIPLIER);
            clearPendingDamage();
            updateHud();
        }
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent) {
            onDamage((EntityDamageEvent) event);
        } else if (event instanceof EntityRegainHealthEvent) {
            onHeal((EntityRegainHealthEvent) event);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!event.getEntity().equals(getPlayer())) {
            return;
        }
        // 지연 피해 적용 중이면 무시 (재귀 방지)
        if (applyingDamage) {
            return;
        }

        double damage = getCalculatedFinalDamage(event) * PASSIVE_DAMAGE_MULTIPLIER;
        Entity source = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
        event.setCancelled(true);
        applyKnockback(source);
        scheduleDamage(damage);
        updateHud();
    }

    private void onHeal(EntityRegainHealthEvent event) {
        if (!event.getEntity().equals(getPlayer())) {
            return;
        }
        double amount = event.getAmount();
        event.setCancelled(true);
        scheduleHeal(amount);
    }

    @Override
    protected void onDestroy() {
    }

    private void scheduleDamage(double damage) {
        pendingDamage += damage;
        deferredActions.add(new DeferredAction(damage, DELAY_SECONDS * 20) {
            @Override
            void run() {
                applyDamage(getAmount());
                pendingDamage = Math.max(0, pendingDamage - getAmount());
                updateHud();
            }
        });
    }

    private void applyKnockback(Entity source) {
        Player player = getPlayer();
        if (player == null || player.isDead()) {
            return;
        }
        Vector knockback = createKnockbackVelocity(player, source);
        double healthBefore = player.getHealth();
        double maxHealth = getMaxHealth(player);
        if (healthBefore <= MINIMAL_DAMAGE && maxHealth > healthBefore) {
            player.setHealth(Math.min(maxHealth, healthBefore + MINIMAL_DAMAGE));
        }
        applyingDamage = true;
        try {
            if (source != null && source.isValid()) {
                player.damage(MINIMAL_DAMAGE, source);
            } else {
                player.damage(MINIMAL_DAMAGE);
            }
        } finally {
            applyingDamage = false;
        }
        if (!player.isDead()) {
            player.setHealth(Math.min(maxHealth, healthBefore));
            if (knockback != null) {
                player.setVelocity(knockback);
            }
        }
    }

    private void applyDamage(double damage) {
        Player player = getPlayer();
        if (player == null || player.isDead()) {
            return;
        }
        applyingDamage = true;
        try {
            double newHealth = player.getHealth() - damage;
            if (newHealth <= 0) {
                player.setHealth(0);
            } else {
                player.setHealth(newHealth);
            }
        } finally {
            applyingDamage = false;
        }
    }

    private void scheduleHeal(double amount) {
        deferredActions.add(new DeferredAction(amount, DELAY_SECONDS * 20) {
            @Override
            void run() {
                Player player = getPlayer();
                if (player == null || player.isDead()) {
                    return;
                }
                double maxHealth = getMaxHealth(player);
                player.setHealth(Math.min(maxHealth, player.getHealth() + getAmount()));
            }
        });
    }

    private double getMaxHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        return maxHealth != null ? maxHealth.getValue() : 20.0;
    }

    private Vector createKnockbackVelocity(Player player, Entity source) {
        if (source == null || !source.isValid()) {
            return null;
        }
        Vector direction;
        if (source instanceof Projectile projectile && projectile.getVelocity().lengthSquared() > 1.0E-6) {
            direction = projectile.getVelocity().clone().setY(0);
        } else {
            direction = player.getLocation().toVector().subtract(source.getLocation().toVector()).setY(0);
        }
        if (direction.lengthSquared() <= 1.0E-6) {
            return null;
        }
        direction.normalize().multiply(0.42);
        Vector velocity = player.getVelocity().clone().multiply(0.5);
        velocity.setX(velocity.getX() + direction.getX());
        velocity.setZ(velocity.getZ() + direction.getZ());
        velocity.setY(Math.min(0.4, Math.max(0.2, velocity.getY() + 0.35)));
        return velocity;
    }

    private void clearPendingDamage() {
        deferredActions.clear();
        pendingDamage = 0;
    }

    @Override
    public void onTick(int tick) {
        if (deferredActions.isEmpty()) {
            return;
        }
        deferredActions.removeIf(action -> {
            action.tick();
            if (action.isReady()) {
                action.run();
                return true;
            }
            return false;
        });
    }

    private abstract static class DeferredAction {
        private final double amount;
        private int remainingTicks;

        DeferredAction(double amount, int delayTicks) {
            this.amount = amount;
            this.remainingTicks = delayTicks;
        }

        double getAmount() {
            return amount;
        }

        void tick() {
            remainingTicks--;
        }

        boolean isReady() {
            return remainingTicks <= 0;
        }

        abstract void run();
    }

    private void updateHud() {
        if (pendingDamage <= 0) {
            pendingGauge.clear();
            return;
        }
        double progress = Math.min(1.0, pendingDamage / MAX_PENDING_DAMAGE);
        String damageText = formatDamageText(pendingDamage);
        Component title = Component.text("미뤄진 피해 ", NamedTextColor.RED)
                .append(Component.text(damageText, NamedTextColor.WHITE))
                .append(Component.text(" (" + DELAY_SECONDS + "초 후)", NamedTextColor.GRAY));
        pendingGauge.update(title, progress);
    }

    private String formatDamageText(double damage) {
        if (damage < 1.0) {
            return String.format("%.2f", damage);
        }
        return String.format("%.1f", damage);
    }
}
