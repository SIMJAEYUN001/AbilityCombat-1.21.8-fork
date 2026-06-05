package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.concurrent.ThreadLocalRandom;

@AbilityManifest(name = "테러리스트 (Terrorist)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 폭파]§f §8(쿨타임: 50초)",
        "§7자신의 주변에 §f20개§7의 TNT를 소환합니다",
        "§7- §f10개§7: 반경 §f10칸§7 원형 테두리 배치",
        "§7- §f10개§7: 반경 §f9칸§7 내부 랜덤 배치",
        "",
        "§e§l[패시브 - 방폭복]",
        "§7폭발 피해를 받지 않습니다"
}, summarize = {
        "§7철괴 우클릭§f: TNT 20개 폭파",
        "§7패시브§f: 폭발 면역"
})
public class Terrorist extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 50;
    private static final int TNT_COUNT = 10;
    private static final double CIRCLE_RADIUS = 10.0;
    private static final double RANDOM_RADIUS = 9.0;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);

    public Terrorist(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageEvent.class);
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
        spawnTnt();
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
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    private void spawnTnt() {
        Player player = getPlayer();
        World world = player.getWorld();
        Location center = player.getLocation();

        // 1 원형 테두리에 균등 배치 (레거시의 circle.toLocations)
        for (int i = 0; i < TNT_COUNT; i++) {
            double angle = (2 * Math.PI / TNT_COUNT) * i;
            double x = Math.cos(angle) * CIRCLE_RADIUS;
            double z = Math.sin(angle) * CIRCLE_RADIUS;
            Location loc = center.clone().add(x, 0.5, z);
            TNTPrimed tnt = world.spawn(loc, TNTPrimed.class);
            tnt.setFuseTicks(50);
            tnt.setSource(player);
        }

        // 2 내부 랜덤 배치 (레거시의 getRandomLocations)
        for (int i = 0; i < TNT_COUNT; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double radius = ThreadLocalRandom.current().nextDouble(2.0, RANDOM_RADIUS);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location loc = center.clone().add(x, 0.5, z);
            TNTPrimed tnt = world.spawn(loc, TNTPrimed.class);
            tnt.setFuseTicks(50);
            tnt.setSource(player);
        }
    }
}
