package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.AbilityTickManager;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.Participant;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "너만을 위해 (ForYouOnly)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 너만을 위해]§f §8(쿨타임: 120초)",
        "§7사용 시 최대 체력이 §c10§7 영구 감소합니다.",
        "§7현재 체력이 §c10 미만§7이면 즉사하고 효과가 발동하지 않습니다.",
        "§7사망한 팀원 1명을 §a체력 4 / 최대 체력 20§7으로 부활시킵니다.",
        "§7사망한 팀원이 없으면 §e흡수 5§7와 §c주는 피해 +50% 60초§7를 얻습니다.",
        "§8팀전/2인전 전용. 같은 능력으로 줄어든 최대 체력은 부활해도 유지됩니다."
}, summarize = {
        "§7철괴 우클릭§f: 최대 체력 10 소모",
        "§7팀원 사망§f: 체력 4로 부활",
        "§7사망 팀원 없음§f: 흡수 5 + 피해 +50% 60초"
})
public class ForYouOnly extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 120;
    private static final double HEALTH_COST = 10.0;
    private static final double REVIVE_HEALTH = 4.0;
    private static final double REVIVE_MAX_HEALTH = 20.0;
    private static final double ABSORPTION_AMOUNT = 5.0;
    private static final int BUFF_TICKS = 1200;
    private static final double DAMAGE_MULTIPLIER = 1.5;
    private static final Map<UUID, Double> REDUCED_MAX_HEALTH = new HashMap<>();

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int buffEndTick;
    private double grantedAbsorption;

    public ForYouOnly(Participant participant) {
        super(participant);
    }

    public static void clearReducedHealth() {
        REDUCED_MAX_HEALTH.clear();
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        clearFallbackAbsorption();
        buffEndTick = 0;
        unregisterTick();
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
        Player player = getPlayer();
        GameManager gameManager = getGameManager();
        if (player == null || gameManager == null || !gameManager.isTeamMode()) {
            if (player != null) {
                player.sendMessage("§c너만을 위해는 팀전/2인전에서만 사용할 수 있습니다.");
            }
            return false;
        }
        if (player.getHealth() < HEALTH_COST) {
            player.setHealth(0.0);
            return true;
        }
        reduceMaxHealth(player, HEALTH_COST);

        Player deadTeammate = gameManager.getTeammates(player, false).stream()
                .filter(teammate -> !gameManager.isAlive(teammate))
                .min(Comparator.comparingDouble(teammate -> teammate.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
        if (deadTeammate != null && reviveTeammate(gameManager, player, deadTeammate)) {
            startCooldown();
            return true;
        }
        applyFallbackBuff(player);
        startCooldown();
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (!(event instanceof EntityDamageByEntityEvent damageEvent)
                || (event instanceof Cancellable cancellable && cancellable.isCancelled())) {
            return;
        }
        Player player = getPlayer();
        if (player != null && buffEndTick > AbilityTickManager.getGlobalTick() && damageEvent.getDamager().equals(player)) {
            scaleOutgoingDamage(damageEvent, DAMAGE_MULTIPLIER);
        }
    }

    @Override
    public void onTick(int tick) {
        if (buffEndTick > 0 && tick >= buffEndTick) {
            clearFallbackAbsorption();
            buffEndTick = 0;
        }
    }

    private boolean reviveTeammate(GameManager gameManager, Player owner, Player teammate) {
        AttributeInstance maxHealth = teammate.getAttribute(Attribute.MAX_HEALTH);
        double currentMax = REDUCED_MAX_HEALTH.getOrDefault(teammate.getUniqueId(),
                maxHealth != null ? maxHealth.getBaseValue() : REVIVE_MAX_HEALTH);
        double reviveMax = isSameAbility(teammate) && currentMax < REVIVE_MAX_HEALTH ? currentMax : REVIVE_MAX_HEALTH;
        boolean revived = gameManager.reviveParticipant(teammate, owner.getLocation(), reviveMax, REVIVE_HEALTH);
        if (revived) {
            owner.playSound(owner.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
            teammate.playSound(teammate.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
        }
        return revived;
    }

    private boolean isSameAbility(Player player) {
        GameManager gameManager = getGameManager();
        Participant participant = gameManager != null ? gameManager.getParticipant(player.getUniqueId()) : null;
        return participant != null && participant.getAbilityDefinition() != null
                && getManifest().name().equals(participant.getAbilityDefinition().getName());
    }

    private void reduceMaxHealth(Player player, double amount) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double nextMax = Math.max(1.0, maxHealth.getBaseValue() - amount);
        maxHealth.setBaseValue(nextMax);
        REDUCED_MAX_HEALTH.put(player.getUniqueId(), nextMax);
        player.setHealth(Math.max(1.0, Math.min(nextMax, player.getHealth() - amount)));
    }

    private void applyFallbackBuff(Player player) {
        clearFallbackAbsorption();
        player.setAbsorptionAmount(player.getAbsorptionAmount() + ABSORPTION_AMOUNT);
        grantedAbsorption = ABSORPTION_AMOUNT;
        buffEndTick = AbilityTickManager.getGlobalTick() + BUFF_TICKS;
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
    }

    private void clearFallbackAbsorption() {
        if (grantedAbsorption <= 0.0) {
            return;
        }
        Player player = getPlayer();
        if (player != null) {
            player.setAbsorptionAmount(Math.max(0.0, player.getAbsorptionAmount() - grantedAbsorption));
        }
        grantedAbsorption = 0.0;
    }

    private void startCooldown() {
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
    }

    private GameManager getGameManager() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin == null ? null : plugin.getGameManager();
    }
}
