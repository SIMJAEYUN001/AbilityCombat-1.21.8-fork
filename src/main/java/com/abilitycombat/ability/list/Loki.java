package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.ParticleUtil;
import com.abilitycombat.utils.LocationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AbilityManifest(name = "로키 (Loki)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.GOD, explain = {
        "§e§l[패시브 - 기만]",
        "§7같은 적을 근접 공격할 때마다 스택이 쌓이며,",
        "§7적의 최근 이동 방향으로 짧게 순간이동합니다.",
        "",
        "§7§f5스택§7이 쌓이면 적의 §c반대 방향§7(등 뒤)으로",
        "§7순간이동하며 스택이 초기화됩니다.",
        "",
        "§e§l[철괴 우클릭 - 배후 습격]§f §8(쿨타임: 15초)",
        "§7§f20칸§7 내 바라보는 적의 등 뒤로",
        "§7즉시 순간이동합니다."
}, summarize = {
        "§7근접 공격§f: 스택 순간이동",
        "§7철괴 우클릭§f: 배후 습격"
})
public class Loki extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 15;
    private static final double TELEPORT_OFFSET = 1.6;
    private static final double LOOK_RANGE = 20.0;
    private static final int MAX_STACK = 5;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final Map<UUID, TextDisplay> holograms = new HashMap<>();
    private UUID lastTarget;
    private int stack;

    public Loki(Participant participant) {
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
        clearHolograms();
    }

    @Override
    protected void onDestroy() {
        clearHolograms();
    }

    @Override
    public boolean activeSkill(Material material, ActiveHandler.ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ActiveHandler.ClickType.RIGHT_CLICK) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, getPlayer(), LOOK_RANGE,
                LocationUtil.withValidTarget(getPlayer(), entity -> !entity.equals(getPlayer())));
        if (target == null) {
            return false;
        }
        teleportOpposite(target);
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
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
        if (lastTarget == null || !lastTarget.equals(target.getUniqueId())) {
            clearHologram(lastTarget);
            lastTarget = target.getUniqueId();
            stack = 0;
        }
        stack++;
        if (stack >= MAX_STACK) {
            stack = 0;
            teleportOpposite(target);
        } else {
            teleportAlong(target);
        }
        updateHologram(target);
    }

    private void teleportAlong(LivingEntity target) {
        Location destination = getOffsetLocation(target, false);
        teleportFacingTarget(destination, target);
        playTeleportEffect(destination);
    }

    private void teleportOpposite(LivingEntity target) {
        Location destination = getOffsetLocation(target, true);
        teleportFacingTarget(destination, target);
        playTeleportEffect(destination);
    }

    private Location getOffsetLocation(LivingEntity target, boolean behind) {
        Vector direction = target.getLocation().getDirection();
        if (direction.lengthSquared() == 0) {
            direction = getPlayer().getLocation().getDirection();
        }
        Vector offset = direction.normalize().multiply(behind ? -TELEPORT_OFFSET : TELEPORT_OFFSET);
        Location location = target.getLocation().clone().add(offset);
        if (location.getBlock().getType().isSolid()) {
            location.add(0, 1.0, 0);
            if (location.getBlock().getType().isSolid()) {
                location.add(0, 1.0, 0);
            }
        }
        return location;
    }

    private void teleportFacingTarget(Location destination, LivingEntity target) {
        if (destination == null) {
            return;
        }
        Location teleportLocation = destination.clone();
        if (target != null && target.getWorld() != null && teleportLocation.getWorld() != null
                && teleportLocation.getWorld().equals(target.getWorld())) {
            Vector lookVector = target.getEyeLocation().toVector().subtract(teleportLocation.toVector());
            if (lookVector.lengthSquared() > 1.0E-6) {
                teleportLocation.setDirection(lookVector);
            }
        }
        getPlayer().teleport(teleportLocation);
    }

    private void playTeleportEffect(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
        ParticleUtil.spawnParticle(location.getWorld(), Particle.PORTAL, location, 16, 0.3, 0.5, 0.3, 0.1, 2, 0);
    }

    private void updateHologram(LivingEntity target) {
        if (target == null) {
            return;
        }
        TextDisplay display = holograms.get(target.getUniqueId());
        if (display == null || display.isDead()) {
            display = target.getWorld().spawn(target.getLocation(), TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setSeeThrough(true);
                entity.setShadowed(false);
                entity.setViewRange(32f);
            });
            holograms.put(target.getUniqueId(), display);
        }
        display.text(Component.text(buildStackText(), NamedTextColor.YELLOW));
        display.teleport(target.getLocation().clone().add(0, 2.2, 0));
    }

    private void refreshHolograms() {
        for (Map.Entry<UUID, TextDisplay> entry : holograms.entrySet()) {
            TextDisplay display = entry.getValue();
            if (display == null || display.isDead()) {
                continue;
            }
            if (display.getWorld() == null) {
                continue;
            }
            var entity = display.getWorld().getEntity(entry.getKey());
            LivingEntity target = entity instanceof LivingEntity living ? living : null;
            if (target == null || target.isDead()) {
                display.remove();
                continue;
            }
            if (target != null) {
                display.teleport(target.getLocation().clone().add(0, 2.2, 0));
            }
        }
        holograms.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isDead());
    }

    private void clearHolograms() {
        for (TextDisplay display : holograms.values()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        holograms.clear();
    }

    private void clearHologram(UUID uuid) {
        if (uuid == null) {
            return;
        }
        TextDisplay display = holograms.remove(uuid);
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    private String buildStackText() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < MAX_STACK; i++) {
            builder.append(i < stack ? "●" : "○");
        }
        return builder.toString();
    }

    @Override
    public void onTick(int tick) {
        if (tick % 10 == 0) {
            refreshHolograms();
        }
    }
}
