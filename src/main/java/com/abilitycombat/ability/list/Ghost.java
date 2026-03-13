package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

@AbilityManifest(name = "유령 (Ghost)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.OTHERS, explain = {
        "§e§l[철괴 우클릭 - 유령화]§f §8(기본 쿨타임: 8초)",
        "§7§f2초§7간 §b무적§7, §b투명§7, §b벽 통과§7 상태로",
        "§7바라보는 방향으로 §f8칸§7 이동합니다.",
        "",
        "§e§l[패시브 - 혼]",
        "§7유령화 사용 시마다 쿨타임이 §c1초§7씩 증가합니다.",
        "§7플레이어를 처치하면 쿨타임이 §a0초§7로 초기화됩니다.",
        "",
        "§e§l[패시브 - 악령]",
        "§7자신을 죽인 플레이어에게 §f25초§7간",
        "§8약화§7와 §8둔화 II§7 효과를 부여합니다."
}, summarize = {
        "§7철괴 우클릭§f: 유령화 대시 (8칸)",
        "§7처치 시§f: 쿨타임 초기화"
})
public class Ghost extends AbilityBase implements ActiveHandler {

    private static final int BASE_COOLDOWN = 8;
    private static final int GHOST_SECONDS = 2;
    private static final double DASH_DISTANCE = 8.0;
    private static final int CURSE_SECONDS = 25;

    private int remainingGhostSeconds = 0;
    private Cooldown cooldown;
    private int cooldownExtra;
    private boolean ghosting;
    private boolean storedInvulnerable;

    public Ghost(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageByEntityEvent.class);
        subscribeEvent(PlayerDeathEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        endGhost();
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ActiveHandler.ClickType.RIGHT_CLICK) {
            return false;
        }
        if (isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        if (ghosting) {
            return false;
        }
        startGhost();
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        } else if (event instanceof PlayerDeathEvent) {
            onDeath((PlayerDeathEvent) event);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (ghosting && event.getEntity().equals(getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (ghosting && event.getDamager().equals(getPlayer())) {
            event.setCancelled(true);
        }
    }

    private void onDeath(PlayerDeathEvent event) {
        if (event.getEntity().equals(getPlayer())) {
            Player killer = event.getEntity().getKiller();
            if (killer != null) {
                applyCurse(killer);
            }
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null && killer.equals(getPlayer())) {
            cooldownExtra = 0;
            if (cooldown != null && cooldown.isCooldown()) {
                cooldown.stop(true);
            }
        }
    }

    private boolean isCooldown() {
        return cooldown != null && cooldown.isCooldown();
    }

    private void startGhost() {
        Player player = getPlayer();
        storedInvulnerable = player.isInvulnerable();
        ghosting = true;
        player.setInvulnerable(true);
        player.setInvisible(true);
        player.setCollidable(false);

        Vector direction = player.getLocation().getDirection().normalize().multiply(DASH_DISTANCE);
        Location destination = player.getLocation().clone().add(direction);
        if (destination.getBlock().getType().isSolid()) {
            destination.add(0, 1.0, 0);
        }
        player.teleport(destination);

        remainingGhostSeconds = GHOST_SECONDS;
        registerTick();
        int cooldownSeconds = BASE_COOLDOWN + cooldownExtra;
        cooldownExtra++;
        cooldown = new Cooldown(cooldownSeconds);
        cooldown.start();
        applyIronCooldownIfEmpty(cooldownSeconds);
    }

    private void endGhost() {
        if (!ghosting) {
            return;
        }
        Player player = getPlayer();
        player.setInvulnerable(storedInvulnerable);
        player.setInvisible(false);
        player.setCollidable(true);
        ghosting = false;
        remainingGhostSeconds = 0;
    }

    private boolean isGhosting() {
        return remainingGhostSeconds > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            if (isGhosting()) {
                remainingGhostSeconds--;
                if (remainingGhostSeconds <= 0) {
                    endGhost();
                }
            }
        }
    }

    private void applyCurse(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, CURSE_SECONDS * 20, 0, true, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CURSE_SECONDS * 20, 1, true, false));
    }

}
