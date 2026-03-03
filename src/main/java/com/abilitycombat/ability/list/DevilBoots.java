package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.game.Participant;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@AbilityManifest(name = "악마의 부츠 (DevilBoots)", rank = AbilityManifest.Rank.B, species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[패시브 - 화염 걸음]",
        "§7이동하는 곳마다 §c불길§7이 생겨납니다.",
        "§7화염 피해에 면역이며, §b신속§7 효과를 받습니다.",
        "",
        "§e§l[패시브 - 물 약점]",
        "§7물에 닿으면 §b'축축함'§7 상태가 되어",
        "§f5초§7간 능력이 비활성화됩니다."
}, summarize = {
        "§7패시브§f: 화염 걸음 + 신속",
        "§7물§f: 능력 비활성화"
})
public class DevilBoots extends AbilityBase {

    private static final int WET_TICKS = 100;
    private static final int TICK_PERIOD = 2;

    private int wetRemaining;

    public DevilBoots(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
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
        if (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onTick(int tick) {
        if (tick % TICK_PERIOD == 0) {
            Player player = getPlayer();
            Block block = player.getLocation().getBlock();
            if (block.isLiquid()) {
                wetRemaining = WET_TICKS;
            }
            if (wetRemaining > 0) {
                wetRemaining -= TICK_PERIOD;
                return;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false));
            Block feet = player.getLocation().getBlock();
            Block below = feet.getRelative(BlockFace.DOWN);
            if (feet.getType() == Material.AIR && below.getType().isSolid()) {
                feet.setType(Material.FIRE);
            }
        }
    }
}
