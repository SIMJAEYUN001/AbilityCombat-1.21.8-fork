package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

@AbilityManifest(name = "레드비어드 (RedBeard)", species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[철괴 우클릭 - 전투의 포효]§f §8(쿨타임: 25초)",
        "§7주변 §f5칸§7 내 적을 밀쳐내고 §f6의 피해§7를 줍니다",
        "§7사용자는 §f10초간§7 §c힘§7 효과를 얻습니다"
}, summarize = {
        "§7철괴 우클릭§f: 주변 적 밀쳐내기 + 버프"
})
public class RedBeard extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 25;
    private static final double RANGE = 5.0;
    private static final double DAMAGE = 6.0;
    private static final int BUFF_SECONDS = 10;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);

    public RedBeard(Participant participant) {
        super(participant);
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
        if (player == null) {
            return false;
        }
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(player.getLocation(), RANGE, player,
                entity -> !entity.equals(player))) {
            Vector knockback = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize()
                    .multiply(0.8);
            knockback.setY(0.3);
            target.setVelocity(knockback);
            target.damage(DAMAGE, player);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, BUFF_SECONDS * 20, 0, true, false));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.9f);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }
}
