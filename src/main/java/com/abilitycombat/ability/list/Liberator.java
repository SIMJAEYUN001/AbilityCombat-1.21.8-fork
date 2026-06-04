package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "리버레이터 (Liberator)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 해방]§f §8(쿨타임: 50초)",
        "§7영혼을 분리하여 §b무적§7 및 §b타게팅 불가§7 상태로 돌진합니다.",
        "§7지속시간: §f10초§7, 받는 피해: §f25% 감소§7, §b신속 II§7 부여",
        "",
        "§e§l[재사용/시간종료 - 회귀]",
        "§7본체로 돌아가며 해방 중 적에게 입힌 피해의 §c75%§7를",
        "§7고정 피해로 입힙니다."
}, summarize = {
        "§7철괴 우클릭§f: 해방 돌진 (10초)",
        "§7회귀 시§f: 고정 피해 75%"
})
public class Liberator extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 50;
    private static final int RETURN_KILL_COOLDOWN_SECONDS = 10;
    private static final int DURATION_SECONDS = 10;
    private static final double DAMAGE_REDUCTION_MULTIPLIER = 0.75;
    private static final double RETURN_DAMAGE_RATIO = 0.75;
    private static final double FIXED_DAMAGE_TRIGGER = 0.001;
    private static final double FIXED_DAMAGE_MIN_HEALTH = 0.001;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int remainingLiberationSeconds = 0;
    private boolean liberated;
    private Location origin;
    private final Map<UUID, Double> dealtDamage = new HashMap<>();
    private boolean storedInvulnerable;

    public Liberator(Participant participant) {
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
        endLiberation();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (liberated) {
            endLiberation();
            return true;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        startLiberation();
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!liberated) {
            return;
        }
        if (event.getDamager().equals(getPlayer()) && event.getEntity() instanceof LivingEntity target) {
            dealtDamage.merge(target.getUniqueId(), getCalculatedFinalDamage(event), Double::sum);
        }
        if (event.getEntity().equals(getPlayer())) {
            scaleIncomingDamage(event, DAMAGE_REDUCTION_MULTIPLIER);
        }
    }

    @Override
    protected void onDestroy() {
        endLiberation();
    }

    private void startLiberation() {
        Player player = getPlayer();
        origin = player.getLocation().clone();
        dealtDamage.clear();
        storedInvulnerable = player.isInvulnerable();
        player.setInvulnerable(false);
        player.setInvisible(true);
        player.setCollidable(false);
        // 돌진 속도 3배 상향 (1.6 -> 4.8)
        Vector dash = player.getLocation().getDirection().normalize().multiply(4.8);
        dash.setY(0.0);
        player.setVelocity(dash);
        // 신속 2 효과 10초 부여
        player.addPotionEffect(
                new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, DURATION_SECONDS * 20, 1));
        liberated = true;
        remainingLiberationSeconds = DURATION_SECONDS;
        registerTick();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
    }

    private void endLiberation() {
        if (!liberated) {
            return;
        }
        liberated = false;
        Player player = getPlayer();
        player.setInvulnerable(storedInvulnerable);
        player.setInvisible(false);
        player.setCollidable(true);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        if (com.abilitycombat.AbilityCombat.getPlugin() != null) {
            org.bukkit.Bukkit.getOnlinePlayers()
                    .forEach(other -> other.showPlayer(com.abilitycombat.AbilityCombat.getPlugin(), player));
        }
        if (origin != null) {
            player.teleport(origin);
        }
        boolean killed = applyReturnDamage(player);
        if (killed) {
            if (!cooldown.isCooldown()) {
                cooldown.start();
            }
            cooldown.setCount(Math.min(cooldown.getCount(), RETURN_KILL_COOLDOWN_SECONDS));
            applyIronCooldownIfEmpty(RETURN_KILL_COOLDOWN_SECONDS);
        }
        origin = null;
        dealtDamage.clear();
        remainingLiberationSeconds = 0;
    }

    private boolean isLiberated() {
        return remainingLiberationSeconds > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            if (isLiberated()) {
                remainingLiberationSeconds--;
                if (remainingLiberationSeconds <= 0) {
                    endLiberation();
                }
            }
        }
    }

    private boolean applyReturnDamage(Player player) {
        boolean killed = false;
        for (Map.Entry<UUID, Double> entry : dealtDamage.entrySet()) {
            org.bukkit.entity.Entity entity = player.getWorld().getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity target) || target.isDead()) {
                continue;
            }
            double damage = entry.getValue() * RETURN_DAMAGE_RATIO;
            if (damage <= 0) {
                continue;
            }
            applyFixedDamage(target, damage, player);
            if (target.isDead() || target.getHealth() <= 0.0) {
                killed = true;
            }
        }
        return killed;
    }

    private void applyFixedDamage(LivingEntity target, double damage, Player source) {
        if (target == null || target.isDead() || damage <= 0) {
            return;
        }
        double newHealth = target.getHealth() - damage;
        target.setNoDamageTicks(0);
        if (newHealth <= 0) {
            target.setHealth(FIXED_DAMAGE_MIN_HEALTH);
            target.damage(FIXED_DAMAGE_TRIGGER, source);
        } else {
            target.setHealth(newHealth);
            target.damage(FIXED_DAMAGE_TRIGGER, source);
        }
    }

}
