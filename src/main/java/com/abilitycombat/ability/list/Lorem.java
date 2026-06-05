package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.entity.CustomEntity;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "로렘 (Lorem)", species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 검의 길]",
        "§7검으로 공격 시 §c+1의 추가 피해§7를 입힙니다",
        "",
        "§e§l[검 우클릭 - 검기]",
        "§7검기를 발사합니다 적중 시 §c4의 피해§7 + §e순간이동§7",
        "§f3회 연속§7 적중 시 §c7의 피해§7를 줍니다",
        "",
        "§7검기가 빗나갈 경우, §f7초§7간 재사용이 불가능합니다"
}, summarize = {
        "§7검 우클릭§f: 검기 발사 (4/7 피해 + 순간이동)",
        "§7패시브§f: 검 공격 시 +1 추가 피해, 미적중 시 7초 쿨타임"
})
public class Lorem extends AbilityBase implements ActiveHandler {

    private static final int SLASH_LIFETIME_TICKS = 20;
    private static final double SLASH_SPEED = 1.8;
    private static final int COOLDOWN_SECONDS = 7;

    private int hitStack = 0;
    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private final Set<UUID> slashDamaged = new java.util.HashSet<>();

    public Lorem(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (!isSword(material) || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        Player player = getPlayer();
        Vector velocity = player.getLocation().getDirection().normalize().multiply(SLASH_SPEED);
        Location start = player.getEyeLocation().clone().add(velocity.clone().multiply(0.5));
        Slash slash = new Slash(start, velocity);
        slash.setSource(player);
        slash.spawn();
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent e) {
            onDamageByEntity(e);
        }
    }

    private void onDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !damager.equals(getPlayer())) {
            return;
        }
        Material hand = damager.getInventory().getItemInMainHand().getType();
        if (isSword(hand)) {
            event.setDamage(Math.max(0.0, event.getDamage() + 1.0));
        }
    }

    private void applySlashHit(LivingEntity target) {
        if (target == null || !com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), target)) {
            return;
        }
        slashDamaged.add(target.getUniqueId());
        hitStack++;
        double damage = hitStack >= 3 ? 7.0 : 4.0;
        if (hitStack >= 3) {
            hitStack = 0;
        }
        target.setNoDamageTicks(0);
        target.damage(damage, getPlayer());
        getPlayer().teleport(target.getLocation());

        // Remove from slashDamaged after a short delay to allow the damage to go
        // through
        AbilityCombat.getPlugin().getServer().getScheduler().runTaskLater(AbilityCombat.getPlugin(),
                () -> slashDamaged.remove(target.getUniqueId()), 1L);
    }

    private class Slash extends CustomEntity {
        private boolean hit = false;

        public Slash(Location start, Vector velocity) {
            super(start.getWorld(), start);
            setVelocity(velocity);
            setGravity(0.0);
            setDrag(0.0);
            setMaxAge(SLASH_LIFETIME_TICKS);
            resizeBoundingBox(-0.3, -0.3, -0.3, 0.3, 0.3, 0.3);
        }

        @Override
        protected void onTick() {
            ParticleUtil.spawnParticle(getWorld(), Particle.SWEEP_ATTACK, getLocation(), 1, 0, 0, 0, 0, 1, 0);
        }

        @Override
        protected boolean onHitEntity(LivingEntity entity, Location hitLocation) {
            hit = true;
            applySlashHit(entity);
            return true;
        }

        @Override
        protected boolean onHitBlock(Block block, Location hitLocation) {
            if (!hit) {
                cooldown.start();
            }
            return true;
        }

        @Override
        protected void onRemove() {
            if (!hit) {
                cooldown.start();
            }
        }
    }

    private boolean isSword(Material material) {
        return material.name().endsWith("_SWORD");
    }
}
