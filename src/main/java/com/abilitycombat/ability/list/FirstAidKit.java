package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.GameManager;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Comparator;

@AbilityManifest(name = "구급상자 (FirstAidKit)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 응급 식별]",
        "§7체력이 §c50% 미만§7인 아군이 발광합니다.",
        "",
        "§e§l[철괴 우클릭 - 응급 처치]§f §8(공유 쿨타임: 45초)",
        "§7바라본 팀원 또는 주변 §f12칸§7 내 팀원의 체력을 §a14§7 회복합니다.",
        "§e§l[철괴 좌클릭 - 자가 처치]§f §8(공유 쿨타임: 45초)",
        "§7자신의 체력을 §a7§7 회복합니다."
}, summarize = {
        "§7패시브§f: 체력 50% 미만 아군 발광",
        "§7우클릭§f: 팀원 체력 14 회복",
        "§7좌클릭§f: 자신 체력 7 회복"
})
public class FirstAidKit extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 45;
    private static final double ALLY_HEAL = 14.0;
    private static final double SELF_HEAL = 7.0;
    private static final double RANGE = 12.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);

    public FirstAidKit(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT) {
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
        boolean healed = clickType == ClickType.LEFT_CLICK ? heal(player, SELF_HEAL) : healAlly(player);
        if (healed) {
            cooldown.start();
            applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
            player.playSound(player.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 0.7f, 1.35f);
        }
        return healed;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 10 != 0) {
            return;
        }
        Player owner = getPlayer();
        GameManager gameManager = getGameManager();
        if (owner == null || gameManager == null || !gameManager.isTeamMode()) {
            return;
        }
        for (Player teammate : gameManager.getTeammates(owner, true)) {
            if (isBelowHalf(teammate)) {
                teammate.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 14, 0, false, false));
            }
        }
    }

    private boolean healAlly(Player owner) {
        GameManager gameManager = getGameManager();
        if (gameManager == null || !gameManager.isTeamMode()) {
            owner.sendMessage("§c치료할 팀원이 없습니다.");
            return false;
        }
        Player target = LocationUtil.getEntityLookingAt(Player.class, owner, RANGE,
                player -> gameManager.areTeammates(owner, player));
        if (target == null) {
            target = gameManager.getTeammates(owner, true).stream()
                    .filter(teammate -> teammate.getWorld().equals(owner.getWorld()))
                    .filter(teammate -> teammate.getLocation().distanceSquared(owner.getLocation()) <= RANGE * RANGE)
                    .min(Comparator.comparingDouble(teammate -> teammate.getLocation().distanceSquared(owner.getLocation())))
                    .orElse(null);
        }
        if (target == null) {
            owner.sendMessage("§c12칸 내 치료할 팀원이 없습니다.");
            return false;
        }
        return heal(target, ALLY_HEAL);
    }

    private boolean heal(Player target, double amount) {
        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        double next = Math.min(max, target.getHealth() + amount);
        if (next <= target.getHealth()) {
            return false;
        }
        target.setHealth(next);
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.6f);
        return true;
    }

    private boolean isBelowHalf(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        return player.getHealth() < max * 0.5;
    }

    private GameManager getGameManager() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin == null ? null : plugin.getGameManager();
    }
}
