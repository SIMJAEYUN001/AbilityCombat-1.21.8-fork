package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;

@AbilityManifest(name = "루베르 (Ruber)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 스택 시스템]",
        "§7공격/스킬 사용마다 §c스택§7이 쌓입니다. (최대 §f2§7)",
        "§f2스택§7 도달 시 다음 스킬이 §c강화§7됩니다.",
        "",
        "§e§l[철괴 우클릭 - 흡혈]§f §8(쿨타임: 8초)",
        "§7§f10칸§7 내 적에게 §c4의 피해§7 + 체력 회복 (§f2§7)",
        "§7강화: §c6의 피해§7 + 위더 효과 부여",
        "",
        "§e§l[철괴 좌클릭 - 전염병 창궐]§f §8(쿨타임: 20초)",
        "§7주변 §f10칸§7 이내 모든 적에게 §8위더§7 효과 부여",
        "§7강화: 위더 지속시간 증가 + §c2의 피해§7"
}, summarize = {
        "§7철괴 우클릭§f: 흡혈 (4/6 피해)",
        "§7철괴 좌클릭§f: 전염병 (위더)"
})
public class Ruber extends AbilityBase implements ActiveHandler {

    private static final int DRAIN_COOLDOWN = 8;
    private static final int PLAGUE_COOLDOWN = 20;
    private static final int RANGE = 10;

    private final Cooldown drainCooldown = new Cooldown(DRAIN_COOLDOWN);
    private final Cooldown plagueCooldown = new Cooldown(PLAGUE_COOLDOWN);
    private int stack = 0;

    public Ruber(Participant participant) {
        super(participant);
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT) {
            return false;
        }
        if (clickType == ClickType.RIGHT_CLICK) {
            return drain();
        }
        if (clickType == ClickType.LEFT_CLICK) {
            return plague();
        }
        return false;
    }

    private boolean drain() {
        if (drainCooldown.isCooldown()) {
            notifyCooldown(drainCooldown);
            return false;
        }
        Player player = getPlayer();
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, player, RANGE,
                entity -> !entity.equals(player));
        if (target == null) {
            return false;
        }
        boolean enhanced = consumeStack();
        double damage = enhanced ? 6.0 : 4.0;
        target.damage(damage, player);
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double maxHealth = attr != null ? attr.getValue() : 20.0;
        player.setHealth(Math.min(maxHealth, player.getHealth() + damage * 0.5));
        if (enhanced) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, true, false));
        }
        drainCooldown.start();
        applyIronCooldownIfEmpty(DRAIN_COOLDOWN);
        return true;
    }

    private boolean plague() {
        if (plagueCooldown.isCooldown()) {
            notifyCooldown(plagueCooldown);
            return false;
        }
        Player player = getPlayer();
        boolean enhanced = consumeStack();
        Collection<LivingEntity> targets = LocationUtil.getNearbyLivingEntities(player.getLocation(), RANGE,
                entity -> !entity.equals(player));
        int duration = enhanced ? 160 : 100;
        for (LivingEntity target : targets) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, duration, 0, true, false));
            if (enhanced) {
                target.damage(2.0, player);
            }
        }
        plagueCooldown.start();
        applyIronCooldownIfEmpty(PLAGUE_COOLDOWN);
        return true;
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    private boolean consumeStack() {
        if (stack >= 2) {
            stack = 0;
            return true;
        }
        return false;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager().equals(getPlayer())) {
            stack = Math.min(2, stack + 1);
        }
    }
}
