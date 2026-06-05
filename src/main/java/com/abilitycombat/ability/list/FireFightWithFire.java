package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.AbilityCombat;
import com.abilitycombat.effect.SharedBurn;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;

@AbilityManifest(name = "이열치열 (FireFightWithFire)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 화염 흡수]",
        "§7자신에게 적용되는 §c화상 스택 피해§7를 받지 않고 회복합니다.",
        "§7이 능력의 화상은 스택당 매초 §c최대 체력의 0.5% 고정 피해§7입니다.",
        "§7일반 화염 피해도 받지 않고 해당 피해만큼 §a체력을 회복§7합니다.",
        "",
        "§e§l[철괴 우클릭 - 발화]§f §8(쿨타임: 20초)",
        "§7주변 §f6칸§7 내 적에게 §c화상 10스택§7과 §c4초 점화§7를 부여합니다."
}, summarize = {
        "§7패시브§f: 화상 스택 피해 → 체력 회복",
        "§7철괴 우클릭§f: 6칸 적에게 화상 10스택"
})
public class FireFightWithFire extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 20;
    private static final int FIRE_TICKS = 80;
    private static final int ACTIVE_BURN_STACKS = 10;
    private static final double ACTIVE_RADIUS = 6.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);

    public FireFightWithFire(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageEvent.class);
        SharedBurn.registerAbsorber(getPlayer());
    }

    @Override
    protected void onDeactivate() {
        SharedBurn.unregisterAbsorber(getPlayer());
    }

    @Override
    protected void onDestroy() {
        SharedBurn.unregisterAbsorber(getPlayer());
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ActiveHandler.ClickType.RIGHT_CLICK) {
            return false;
        }
        if (isInvincible()) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(player.getLocation(), ACTIVE_RADIUS, player,
                entity -> true)) {
            SharedBurn.addStacks(target, player, SharedBurn.BurnProfile.FIRE_FIGHT_WITH_FIRE, ACTIVE_BURN_STACKS,
                    FIRE_TICKS);
        }
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent) {
            onDamage((EntityDamageEvent) event);
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (!event.getEntity().equals(getPlayer())) {
            return;
        }
        if (isInvincible()) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setCancelled(true);
            Player player = getPlayer();
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.min(maxHealth, player.getHealth() + event.getDamage()));
        }
    }

    private boolean isInvincible() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return false;
        }
        GameManager gameManager = plugin.getGameManager();
        if (gameManager == null) {
            return false;
        }
        return gameManager.isInvincible();
    }
}
