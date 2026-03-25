package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

@AbilityManifest(name = "생존 본능 (SurvivalInstinct)", rank = AbilityManifest.Rank.B, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 위기 탈출]",
        "§7단 한 번, 죽음에 이르는 피해를 받을 때",
        "§7체력을 §c3칸(6HP)§7으로 고정하고 살아납니다.",
        "",
        "§7발동 시 주변 §f4칸§7 이내의 적을 밀쳐내고",
        "§f4초§7간 §b무적§7 및 §8공격 불가§7 상태가 되며",
        "§b신속 III§7와 §6흡수 IV§7를 얻습니다.",
        "",
        "§7다른 플레이어를 §c처치§7하면 능력이 재충전됩니다."
}, summarize = {
        "§7패시브§f: 1회 죽음 방지 (체력 6 유지)",
        "§7처치 시§f: 재충전"
})
public class SurvivalInstinct extends AbilityBase {

    private static final int INVULN_SECONDS = 4;
    private static final int ABSORPTION_SECONDS = 15;
    private static final double SURVIVE_HEALTH = 6.0;
    private static final double KNOCK_RADIUS = 4.0;

    private boolean charged = true;
    private boolean invulnerable;
    private int remainingInvulnTicks = 0;

    public SurvivalInstinct(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
        subscribeEvent(EntityDamageByEntityEvent.class);
        subscribeEvent(PlayerDeathEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        invulnerable = false;
        getPlayer().setInvulnerable(false);
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent) {
            onDamage((EntityDamageEvent) event);
        } else if (event instanceof EntityDamageByEntityEvent) {
            onAttack((EntityDamageByEntityEvent) event);
        } else if (event instanceof PlayerDeathEvent) {
            onKill((PlayerDeathEvent) event);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (!charged || invulnerable) {
            return;
        }
        double finalDamage = getCalculatedFinalDamage(event);
        if (player.getHealth() - finalDamage > 0.0) {
            return;
        }
        event.setCancelled(true);
        charged = false;
        triggerSurvival();
    }

    private void onAttack(EntityDamageByEntityEvent event) {
        if (invulnerable && event.getDamager().equals(getPlayer())) {
            event.setCancelled(true);
        }
    }

    private void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && killer.equals(getPlayer())) {
            charged = true;
        }
    }

    @Override
    protected void onDestroy() {
        invulnerable = false;
        getPlayer().setInvulnerable(false);
    }

    private void triggerSurvival() {
        Player player = getPlayer();
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maxHealth, SURVIVE_HEALTH));
        invulnerable = true;
        player.setInvulnerable(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, INVULN_SECONDS * 20, 2, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ABSORPTION_SECONDS * 20, 3, true, false));
        for (LivingEntity entity : player.getWorld().getLivingEntities()) {
            if (entity.equals(player) || !com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), entity)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(player.getLocation()) <= KNOCK_RADIUS * KNOCK_RADIUS) {
                Vector knock = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize()
                        .multiply(1.3);
                knock.setY(0.4);
                entity.setVelocity(knock);
            }
        }
        remainingInvulnTicks = INVULN_SECONDS * 20;
        registerTick();
    }

    @Override
    public void onTick(int tick) {
        if (remainingInvulnTicks > 0) {
            remainingInvulnTicks--;
            if (remainingInvulnTicks <= 0) {
                invulnerable = false;
                getPlayer().setInvulnerable(false);
            }
        }
    }
}
