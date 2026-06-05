package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@AbilityManifest(name = "쇼맨쉽 (ShowmanShip)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 관중]",
        "§7주변 §f10칸§7 이내의 생명체 수에 따라 강화됩니다",
        "§7(플레이어: §f1§7점, 기타: §f0.2§7점)",
        "",
        "§7- §f2점 이상§7: §c힘 II§7 버프 획득",
        "§7- §f4점 이상§7: §c힘 III§7 버프 및 §4처형§7 활성화",
        "",
        "§7점수가 §f4점 이상§7일 때, 체력 §c30% 미만§7인 적을",
        "§7공격 시 §4즉사(처형)§7시킵니다"
}, summarize = {
        "§7패시브§f: 관중 수에 따라 힘 버프",
        "§74점 이상§f: 힘 III + 처형 발동"
})
public class ShowmanShip extends AbilityBase {

    private static final double RANGE = 10.0;
    private static final double EXECUTE_RATIO = 0.3;
    private static final double WEIGHT_OTHER = 0.2;

    private double audienceScore;

    public ShowmanShip(Participant participant) {
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
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !player.equals(getPlayer())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (audienceScore < 4.0) {
            return;
        }
        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (target.getHealth() / maxHealth <= EXECUTE_RATIO) {
            event.setDamage(maxHealth * 10);
        }
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            Player player = getPlayer();
            double score = 0.0;
            for (LivingEntity entity : com.abilitycombat.utils.LocationUtil.getNearbyLivingEntities(
                    player.getLocation(), RANGE, e -> !e.equals(player))) {
                if (entity instanceof Player) {
                    score += 1.0;
                } else {
                    score += WEIGHT_OTHER;
                }
            }
            audienceScore = score;
            if (audienceScore >= 4.0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 2, true, false));
            } else if (audienceScore >= 2.0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 1, true, false));
            }
        }
    }
}
