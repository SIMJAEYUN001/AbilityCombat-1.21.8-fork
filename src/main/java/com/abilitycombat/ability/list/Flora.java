package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@AbilityManifest(name = "플로라 (Flora)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.GOD, explain = {
        "§e§l[패시브 - 자연의 축복]",
        "§7자신과 팀원에게 지속적으로 버프를 부여합니다.",
        "§7버프 범위는 주변 §f8칸§7으로 고정됩니다.",
        "§7효과가 §b신속§7일 때 자신은 §b신속 II§7, 팀원은 §b신속 I§7를 받습니다.",
        "§7효과가 §a재생§7일 때 자신은 §a재생 II§7, 팀원은 §a재생 I§7를 받습니다.",
        "",
        "§e§l[철괴 우클릭 - 효과 변경]§f §8(쿨타임: 3초)",
        "§7버프 효과를 §b신속§7 또는 §a재생§7으로 변경합니다."
}, summarize = {
        "§7패시브§f: 자신 신속 II/재생 II, 팀원 신속 I/재생 I",
        "§7철괴§f: 효과 변경"
})
public class Flora extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 3;
    private static final int RANGE = 8;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private PotionEffectType effectType = PotionEffectType.SPEED;

    public Flora(Participant participant) {
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
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        if (clickType == ActiveHandler.ClickType.RIGHT_CLICK) {
            effectType = (effectType == PotionEffectType.SPEED) ? PotionEffectType.REGENERATION
                    : PotionEffectType.SPEED;
            getPlayer().sendMessage("§a플로라 효과: §f" + (effectType == PotionEffectType.SPEED ? "신속" : "재생"));
            cooldown.start();
            applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
            return true;
        }
        return false;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            Player owner = getPlayer();
            for (Player player : owner.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(owner.getLocation()) <= RANGE * RANGE
                        && canBuffTarget(owner, player)) {
                    int amplifier = owner.equals(player) ? 1 : 0;
                    player.addPotionEffect(new PotionEffect(effectType, 40, amplifier, true, false));
                }
            }
        }
    }

    private boolean canBuffTarget(Player owner, Player target) {
        if (owner == null || target == null) {
            return false;
        }
        if (owner.equals(target)) {
            return true;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin != null
                && plugin.getGameManager() != null
                && plugin.getGameManager().areTeammates(owner, target);
    }
}
