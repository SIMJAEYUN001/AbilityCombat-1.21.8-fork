package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Stun;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "광대 (Clown)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[철괴 우클릭 - 환상]§f §8(쿨타임: 35초)",
        "§7바라보는 방향으로 순간이동하고 §f3초§7간 §b은신§7합니다",
        "§7이후 §f4초§7 안에 재사용하면 이동 전 위치로 돌아가며",
        "§7주변 적을 §f3초§7간 §e기절§7시킵니다",
        "",
        "§e§l[패시브 - 암습]",
        "§7적의 §f뒤§7를 공격하면 §e기절§7시키고 §f1.35배§7 피해를 입힙니다",
        "§7(대상별 쿨타임 10초)"
}, summarize = {
        "§7철괴 우클릭§f: 순간이동 + 은신",
        "§7재사용§f: 원위치 복귀 + 주변 적 기절",
        "§7후방 공격§f: 기절 + §f1.35배§7 피해"
})
public class Clown extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 35;
    private static final int HIDE_SECONDS = 3;
    private static final int RETURN_WINDOW_SECONDS = 4;
    private static final double TELEPORT_DISTANCE = 6.0;
    private static final double FEAR_RADIUS = 5.0;
    private static final int FEAR_TICKS = 60;
    private static final int BACKSTAB_STUN_TICKS = 30;
    private static final double BACKSTAB_DAMAGE_MULTIPLIER = 1.35;
    private static final long BACKSTAB_COOLDOWN_MS = 10000L;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final Map<UUID, Long> lastBackstab = new HashMap<>();

    private Location returnLocation;
    private int returnRemainingSeconds;
    private int hideRemainingSeconds;
    private boolean hidden;

    public Clown(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
        registerTick();
    }

    @Override
    protected void onDeactivate() {
        disableHide();
        returnRemainingSeconds = 0;
        returnLocation = null;
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        Player player = getPlayer();
        if (player == null) {
            return false;
        }
        if (returnRemainingSeconds > 0 && returnLocation != null) {
            player.teleport(returnLocation.clone());
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.1f);
            applyFear(player.getLocation());
            startHide(HIDE_SECONDS);
            returnRemainingSeconds = 0;
            returnLocation = null;
            return true;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        Location origin = player.getLocation().clone();
        Location destination = origin.clone().add(origin.getDirection().normalize().multiply(TELEPORT_DISTANCE));
        destination = adjustTeleport(destination);
        player.teleport(destination);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        startHide(HIDE_SECONDS);
        returnLocation = origin;
        returnRemainingSeconds = RETURN_WINDOW_SECONDS;
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent damage) {
            onDamageByEntity(damage);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !damager.equals(getPlayer())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastBackstab.getOrDefault(target.getUniqueId(), 0L);
        if (now - last < BACKSTAB_COOLDOWN_MS) {
            return;
        }
        if (!isBehind(target, damager)) {
            return;
        }
        lastBackstab.put(target.getUniqueId(), now);
        scaleOutgoingDamage(event, BACKSTAB_DAMAGE_MULTIPLIER);
        Stun.apply(target, BACKSTAB_STUN_TICKS);
    }

    private void startHide(int seconds) {
        hideRemainingSeconds = Math.max(hideRemainingSeconds, seconds);
        if (!hidden) {
            enableHide();
        }
    }

    private void enableHide() {
        Player player = getPlayer();
        if (player == null) {
            return;
        }
        player.setInvisible(true);
        player.setCollidable(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, HIDE_SECONDS * 20, 0, true, false));
        org.bukkit.Bukkit.getOnlinePlayers().forEach(other -> {
            if (!other.equals(player)) {
                other.hidePlayer(com.abilitycombat.AbilityCombat.getPlugin(), player);
            }
        });
        hidden = true;
    }

    private void disableHide() {
        if (!hidden) {
            return;
        }
        Player player = getPlayer();
        if (player == null) {
            hidden = false;
            return;
        }
        player.setInvisible(false);
        player.setCollidable(true);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        org.bukkit.Bukkit.getOnlinePlayers().forEach(other -> {
            other.showPlayer(com.abilitycombat.AbilityCombat.getPlugin(), player);
        });
        hidden = false;
    }

    private void applyFear(Location center) {
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(center, FEAR_RADIUS, getPlayer(),
                entity -> !entity.equals(getPlayer()))) {
            Stun.apply(target, FEAR_TICKS);
        }
    }

    private boolean isBehind(LivingEntity target, Player attacker) {
        Vector targetDir = target.getLocation().getDirection().normalize();
        Vector toAttacker = attacker.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        return targetDir.dot(toAttacker) < -0.3;
    }

    private Location adjustTeleport(Location destination) {
        Location adjusted = destination.clone();
        for (int i = 0; i < 3; i++) {
            if (adjusted.getBlock().isPassable()) {
                return adjusted;
            }
            adjusted.add(0, 1.0, 0);
        }
        return adjusted;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 != 0) {
            return;
        }
        if (returnRemainingSeconds > 0) {
            returnRemainingSeconds--;
            if (returnRemainingSeconds <= 0) {
                returnLocation = null;
            }
        }
        if (hideRemainingSeconds > 0) {
            hideRemainingSeconds--;
            if (hideRemainingSeconds <= 0) {
                disableHide();
            }
        }
    }
}
