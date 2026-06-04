package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Bind;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Locale;

@AbilityManifest(name = "망령화 (WraithForm)", species = AbilityManifest.Species.UNDEAD, explain = {
        "§e§l[패시브 - 망령화]",
        "§7인간 상태에서는 입히는 피해가 §c50% 감소§7합니다.",
        "§7피해를 입힐 때마다 §b망령화 4스택§7을 얻습니다. (최대 100)",
        "§7망령화 §f50스택§7 이상이면 검은 연기와 함께 말뚝과 §f18칸§7 필드를 생성합니다.",
        "§7필드 밖으로 벗어나면 현재 망령화의 §c80%§7를 잃습니다.",
        "§7망령 상태가 풀리면 §f15초§7간 재진입할 수 없습니다.",
        "",
        "§e§l[패시브 - 망령 상태]",
        "§750스택 이상에서는 피해 감소가 사라지고 입히는 피해가 §c20% 증가§7합니다.",
        "§7사거리가 §f10%§7 증가하고 입힌 피해의 §a15%§7만큼 회복합니다.",
        "§7비전투 §f7초§7 이후 §f3초§7마다 망령화가 §c15스택§7 감소합니다.",
        "",
        "§e§l[철괴 우클릭 - 혼령 속박]§f §8(쿨타임: 60초)",
        "§7망령 상태에서 바라본 적을 §f3 + 스택의 5%초§7간 속박합니다."
}, summarize = {
        "§7패시브§f: 피해 시 망령화 +4, 인간 상태 피해 -50%",
        "§750스택 이상§f: 검은 연기 진입, 말뚝/18칸 필드, 피해 +20%, 사거리 +10%, 흡혈 15%",
        "§7철괴 우클릭§f: 망령 상태에서 대상 속박 (60초)"
})
public class WraithForm extends AbilityBase implements ActiveHandler {

    private static final int MAX_STACKS = 100;
    private static final int WRAITH_THRESHOLD = 50;
    private static final int STACK_GAIN_ON_DAMAGE = 4;
    private static final double FIELD_EXIT_STACK_LOSS_RATIO = 0.80;
    private static final int NON_COMBAT_DELAY_MILLIS = 7_000;
    private static final int NON_COMBAT_DRAIN_MILLIS = 3_000;
    private static final int NON_COMBAT_STACK_LOSS = 15;
    private static final int ACTIVE_COOLDOWN_SECONDS = 60;
    private static final int REENTRY_COOLDOWN_MILLIS = 15_000;

    private static final double HUMAN_DAMAGE_PENALTY_PERCENT = 50.0;
    private static final double WRAITH_DAMAGE_BONUS_PERCENT = 20.0;
    private static final double WRAITH_LIFESTEAL_RATIO = 0.15;
    private static final double RANGE_BONUS_SCALAR = 0.10;
    private static final double BASE_BIND_RANGE = 12.0;
    private static final double FIELD_RADIUS = 18.0;
    private static final double FIELD_RADIUS_SQUARED = FIELD_RADIUS * FIELD_RADIUS;
    private static final int FIELD_PARTICLE_POINTS = 72;

    private static final String STATUS_KEY = "wraith:status";
    private static final int HUD_PRIORITY = 4;
    private static final Particle.DustOptions FIELD_DUST = new Particle.DustOptions(Color.fromRGB(93, 52, 168), 1.2f);
    private static final Particle.DustOptions WRAITH_ENTRY_DUST =
            new Particle.DustOptions(Color.fromRGB(12, 9, 16), 1.7f);

    private final Cooldown cooldown = new Cooldown(ACTIVE_COOLDOWN_SECONDS);
    private final BossBarGauge stackGauge = new BossBarGauge("wraith", HUD_PRIORITY, BossBar.Color.PURPLE,
            BossBar.Overlay.NOTCHED_10);
    private int stacks;
    private long lastCombatAt;
    private long lastDrainAt;
    private long reentryBlockedUntil;
    private boolean rangeBonusApplied;
    private WraithStake stake;

    public WraithForm(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        long now = System.currentTimeMillis();
        lastCombatAt = now;
        lastDrainAt = now;
        registerTick();
        subscribeEvent(EntityDamageEvent.class);
        updateHud();
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        unsubscribeEvent(EntityDamageEvent.class);
        removeStake();
        removeRangeBonus();
        clearHud();
    }

    @Override
    protected void onDestroy() {
        removeStake();
        removeRangeBonus();
        clearHud();
    }

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
            return false;
        }
        Player owner = getPlayer();
        if (owner == null) {
            return false;
        }
        if (!isWraith()) {
            showStatus("망령화 50스택 이상에서만 사용할 수 있습니다.", NamedTextColor.RED);
            return false;
        }
        if (cooldown.isCooldown()) {
            notifyCooldown(cooldown);
            return false;
        }
        double range = BASE_BIND_RANGE * (1.0 + RANGE_BONUS_SCALAR);
        LivingEntity target = LocationUtil.getEntityLookingAt(LivingEntity.class, owner, range,
                entity -> isCombatTarget(owner, entity));
        if (target == null) {
            showStatus("바라보는 대상이 없습니다.", NamedTextColor.RED);
            return false;
        }

        int bindTicks = Math.max(1, (int) Math.round((3.0 + stacks * 0.05) * 20.0));
        Bind.apply(target, bindTicks);
        cooldown.start();
        applyIronCooldownIfEmpty(ACTIVE_COOLDOWN_SECONDS);
        touchCombat();

        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.9f, 0.55f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7f, 1.45f);
        spawnBindEffect(target.getLocation());
        showStatus("혼령 속박 " + formatSeconds(bindTicks) + "초", NamedTextColor.AQUA);
        updateHud();
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent damageEvent) {
            onDamage(damageEvent);
        }
    }

    @Override
    public void onTick(int tick) {
        if (tick % 8 == 0) {
            tickField();
            spawnFieldBoundary();
            spawnWraithAmbientEffect();
        }
        if (tick % 20 == 0) {
            tickNonCombatDrain();
            syncWraithState();
            updateHud();
        }
    }

    private void onDamage(EntityDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player owner = getPlayer();
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            return;
        }
        if (event.getEntity().getUniqueId().equals(owner.getUniqueId())) {
            if (getCalculatedFinalDamage(event) > 0.0) {
                touchCombat();
            }
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity) || !isDamageSource(owner, byEntity)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || !isCombatTarget(owner, target)) {
            return;
        }

        boolean wraith = isWraith();
        if (wraith) {
            increaseOutgoingDamage(byEntity, WRAITH_DAMAGE_BONUS_PERCENT);
        } else {
            decreaseOutgoingDamage(byEntity, HUMAN_DAMAGE_PENALTY_PERCENT);
        }

        double dealtDamage = Math.min(getCalculatedFinalDamage(event), target.getHealth());
        if (dealtDamage <= 0.0) {
            return;
        }
        touchCombat();
        addStacks(STACK_GAIN_ON_DAMAGE);
        if (wraith) {
            healLater(dealtDamage * WRAITH_LIFESTEAL_RATIO);
        }
        updateHud();
    }

    private void tickField() {
        if (!isWraith()) {
            removeStake();
            return;
        }
        if (stake == null || !stake.isPresent()) {
            removeStake();
            spawnStake();
            return;
        }
        Player owner = getPlayer();
        Location center = stake.center;
        if (owner == null || center == null || owner.getWorld() != center.getWorld()
                || owner.getLocation().distanceSquared(center) > FIELD_RADIUS_SQUARED) {
            int loss = Math.max(1, (int) Math.ceil(stacks * FIELD_EXIT_STACK_LOSS_RATIO));
            addStacks(-loss);
            showStatus("망령 필드 이탈: -" + loss, NamedTextColor.RED);
            owner = getPlayer();
            if (owner != null) {
                owner.playSound(owner.getLocation(), Sound.ENTITY_WITHER_HURT, 0.75f, 1.6f);
            }
        }
    }

    private void tickNonCombatDrain() {
        if (stacks <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCombatAt < NON_COMBAT_DELAY_MILLIS || now - lastDrainAt < NON_COMBAT_DRAIN_MILLIS) {
            return;
        }
        lastDrainAt = now;
        addStacks(-NON_COMBAT_STACK_LOSS);
        showStatus("비전투 망령화 -" + NON_COMBAT_STACK_LOSS, NamedTextColor.GRAY);
    }

    private void addStacks(int amount) {
        if (amount == 0) {
            return;
        }
        boolean wasWraith = isWraith();
        int before = stacks;
        stacks = Math.max(0, Math.min(MAX_STACKS, stacks + amount));
        if (amount > 0 && hasWraithStacks() && isReentryBlocked()) {
            showStatus("망령화 재진입 대기 " + getReentryRemainingSeconds() + "초", NamedTextColor.RED);
        }
        if (before == stacks) {
            return;
        }
        boolean nowWraith = isWraith();
        if (nowWraith) {
            applyRangeBonus();
        } else {
            removeRangeBonus();
        }
        if (!wasWraith && nowWraith) {
            spawnStake();
            Player owner = getPlayer();
            if (owner != null) {
                spawnWraithEntryEffect(owner);
                owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.45f);
            }
        } else if (wasWraith && !nowWraith) {
            if (!hasWraithStacks()) {
                reentryBlockedUntil = System.currentTimeMillis() + REENTRY_COOLDOWN_MILLIS;
            }
            removeStake();
        } else if (nowWraith && stake == null) {
            spawnStake();
        }
        updateHud();
    }

    private void syncWraithState() {
        if (isWraith()) {
            Player owner = getPlayer();
            if (!rangeBonusApplied && owner != null) {
                spawnWraithEntryEffect(owner);
                owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.45f);
            }
            applyRangeBonus();
            if (stake == null) {
                spawnStake();
            }
        } else {
            removeRangeBonus();
            removeStake();
        }
    }

    private boolean isWraith() {
        return hasWraithStacks() && !isReentryBlocked();
    }

    private boolean hasWraithStacks() {
        return stacks >= WRAITH_THRESHOLD;
    }

    private boolean isReentryBlocked() {
        return System.currentTimeMillis() < reentryBlockedUntil;
    }

    private int getReentryRemainingSeconds() {
        long remaining = Math.max(0L, reentryBlockedUntil - System.currentTimeMillis());
        return (int) Math.ceil(remaining / 1000.0);
    }

    private void touchCombat() {
        long now = System.currentTimeMillis();
        lastCombatAt = now;
        lastDrainAt = now;
    }

    private void spawnStake() {
        Player owner = getPlayer();
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            return;
        }
        removeStake();
        Location spawn = owner.getLocation().clone();
        World world = spawn.getWorld();
        if (world == null) {
            return;
        }
        spawn.setX(spawn.getBlockX() + 0.5);
        spawn.setZ(spawn.getBlockZ() + 0.5);
        Block block = findStakeBlock(spawn);
        if (block == null) {
            return;
        }
        BlockData originalData = block.getBlockData().clone();
        block.setBlockData(Material.COBBLESTONE_WALL.createBlockData(), false);
        Location center = block.getLocation().add(0.5, 0.0, 0.5);
        stake = new WraithStake(block, originalData, center);
        world.playSound(spawn, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 0.55f);
        ParticleUtil.spawnParticle(world, Particle.SOUL_FIRE_FLAME, center.clone().add(0, 1.0, 0),
                34, 0.35, 0.65, 0.35, 0.03, 1, 64);
    }

    private void removeStake() {
        if (stake != null && stake.block != null) {
            World world = stake.center.getWorld();
            stake.block.setBlockData(stake.originalData, false);
            if (world != null) {
                world.playSound(stake.center, Sound.BLOCK_STONE_BREAK, 0.65f, 0.7f);
                ParticleUtil.spawnParticle(world, Particle.BLOCK, stake.center.clone().add(0, 0.7, 0),
                        18, 0.35, 0.45, 0.35, 0.0, Material.COBBLESTONE_WALL.createBlockData(), 1, 64);
            }
        }
        stake = null;
    }

    private Block findStakeBlock(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();
        int[][] offsets = {
                {0, 0},
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1},
                {1, 1},
                {1, -1},
                {-1, 1},
                {-1, -1}
        };
        for (int[] offset : offsets) {
            Block block = world.getBlockAt(baseX + offset[0], baseY, baseZ + offset[1]);
            if (canPlaceStakeBlock(block)) {
                return block;
            }
        }
        return null;
    }

    private boolean canPlaceStakeBlock(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return type.isAir() || (block.isPassable() && !block.isLiquid());
    }

    private void spawnFieldBoundary() {
        if (stake == null || stake.center == null) {
            return;
        }
        Location center = stake.center;
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double y = center.getY() + 0.12;
        for (int i = 0; i < FIELD_PARTICLE_POINTS; i++) {
            double angle = (Math.PI * 2.0 * i) / FIELD_PARTICLE_POINTS;
            Location point = new Location(world,
                    center.getX() + Math.cos(angle) * FIELD_RADIUS,
                    y,
                    center.getZ() + Math.sin(angle) * FIELD_RADIUS);
            ParticleUtil.spawnParticle(world, Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0,
                    FIELD_DUST, 8, 64);
            if (i % 9 == 0) {
                ParticleUtil.spawnParticle(world, Particle.SOUL_FIRE_FLAME, point.clone().add(0, 0.35, 0),
                        1, 0.02, 0.08, 0.02, 0.0, 8, 64);
            }
        }
    }

    private void spawnBindEffect(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Location center = location.clone().add(0, 1.0, 0);
        ParticleUtil.spawnParticle(world, Particle.SOUL_FIRE_FLAME, center,
                20, 0.35, 0.45, 0.35, 0.02, 1, 64);
        ParticleUtil.spawnParticle(world, Particle.REVERSE_PORTAL, center,
                16, 0.25, 0.35, 0.25, 0.04, 1, 64);
    }

    private void spawnWraithAmbientEffect() {
        if (!isWraith()) {
            return;
        }
        Player owner = getPlayer();
        if (owner == null) {
            return;
        }
        World world = owner.getWorld();
        Location center = owner.getLocation().clone().add(0, 1.0, 0);
        ParticleUtil.spawnParticle(world, Particle.LARGE_SMOKE, center,
                10, 0.55, 0.65, 0.55, 0.02, 2, 64);
        ParticleUtil.spawnParticle(world, Particle.SMOKE, center,
                8, 0.75, 0.45, 0.75, 0.02, 2, 64);
        ParticleUtil.spawnParticle(world, Particle.DUST, center,
                5, 0.45, 0.5, 0.45, 0.0, WRAITH_ENTRY_DUST, 2, 64);
    }

    private void spawnWraithEntryEffect(Player owner) {
        Location base = owner.getLocation();
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        Location center = base.clone().add(0, 1.0, 0);
        ParticleUtil.spawnParticle(world, Particle.LARGE_SMOKE, center,
                46, 0.8, 0.85, 0.8, 0.04, 1, 64);
        ParticleUtil.spawnParticle(world, Particle.SMOKE, center,
                34, 1.05, 0.65, 1.05, 0.03, 1, 64);
        ParticleUtil.spawnParticle(world, Particle.DUST, center,
                26, 0.75, 0.7, 0.75, 0.0, WRAITH_ENTRY_DUST, 1, 64);
        for (int i = 0; i < 28; i++) {
            double angle = (Math.PI * 2.0 * i) / 28.0;
            double radius = i % 2 == 0 ? 1.25 : 1.65;
            Location point = base.clone().add(Math.cos(angle) * radius, 0.15 + (i % 3) * 0.28,
                    Math.sin(angle) * radius);
            ParticleUtil.spawnParticle(world, Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0,
                    WRAITH_ENTRY_DUST, 1, 64);
        }
    }

    private void applyRangeBonus() {
        if (rangeBonusApplied) {
            return;
        }
        Player owner = getPlayer();
        if (owner == null) {
            return;
        }
        AttributeInstance range = owner.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        NamespacedKey key = rangeKey();
        if (range == null || key == null) {
            return;
        }
        range.removeModifier(key);
        range.addTransientModifier(new AttributeModifier(key, RANGE_BONUS_SCALAR,
                AttributeModifier.Operation.ADD_SCALAR));
        rangeBonusApplied = true;
    }

    private void removeRangeBonus() {
        if (!rangeBonusApplied) {
            return;
        }
        Player owner = getPlayer();
        NamespacedKey key = rangeKey();
        if (owner != null && key != null) {
            AttributeInstance range = owner.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
            if (range != null) {
                range.removeModifier(key);
            }
        }
        rangeBonusApplied = false;
    }

    private NamespacedKey rangeKey() {
        AbilityCombat plugin = AbilityCombat.getPlugin();
        return plugin == null ? null : new NamespacedKey(plugin, "wraith_form_range");
    }

    private boolean isDamageSource(Player owner, EntityDamageByEntityEvent event) {
        if (event.getDamager().equals(owner)) {
            return true;
        }
        return event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter
                && shooter.equals(owner);
    }

    private boolean isCombatTarget(Player owner, LivingEntity target) {
        return target != null && !(target instanceof ArmorStand) && LocationUtil.isValidTarget(owner, target);
    }

    private void healLater(double amount) {
        if (amount <= 0.0) {
            return;
        }
        AbilityCombat plugin = AbilityCombat.getPlugin();
        if (plugin == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> healOwner(amount));
    }

    private void healOwner(double amount) {
        Player owner = getPlayer();
        if (owner == null || !owner.isOnline() || owner.isDead() || amount <= 0.0) {
            return;
        }
        AttributeInstance maxHealth = owner.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        owner.setHealth(Math.min(max, owner.getHealth() + amount));
    }

    private void updateHud() {
        Player owner = getPlayer();
        if (owner == null) {
            return;
        }
        NamedTextColor stackColor = isWraith() ? NamedTextColor.AQUA : NamedTextColor.GRAY;
        Component message = Component.text("망령화 ", NamedTextColor.DARK_PURPLE)
                .append(Component.text(stacks + "/" + MAX_STACKS, stackColor))
                .append(Component.text(resolveHudStateText(), resolveHudStateColor()));
        stackGauge.update(message, stacks / (double) MAX_STACKS);
    }

    private String resolveHudStateText() {
        if (hasWraithStacks() && isReentryBlocked()) {
            return "  대기 " + getReentryRemainingSeconds() + "초";
        }
        return isWraith() ? "  망령" : "  인간";
    }

    private NamedTextColor resolveHudStateColor() {
        if (hasWraithStacks() && isReentryBlocked()) {
            return NamedTextColor.RED;
        }
        return isWraith() ? NamedTextColor.AQUA : NamedTextColor.WHITE;
    }

    private void showStatus(String message, NamedTextColor color) {
        Player owner = getPlayer();
        if (owner == null) {
            return;
        }
        Component component = Component.text(message, color);
        if (getActionbarChannel() != null) {
            getActionbarChannel().updateForTicks(owner, STATUS_KEY, HUD_PRIORITY + 1, component, 40);
        } else {
            owner.sendActionBar(component);
        }
    }

    private void clearHud() {
        Player owner = getPlayer();
        if (owner == null) {
            return;
        }
        if (getActionbarChannel() != null) {
            getActionbarChannel().clear(owner, STATUS_KEY);
        } else {
            owner.sendActionBar(Component.empty());
        }
        stackGauge.clear();
    }

    private String formatSeconds(int ticks) {
        return String.format(Locale.US, "%.1f", ticks / 20.0);
    }

    private static final class WraithStake {
        private final Block block;
        private final BlockData originalData;
        private final Location center;

        private WraithStake(Block block, BlockData originalData, Location center) {
            this.block = block;
            this.originalData = originalData;
            this.center = center;
        }

        private boolean isPresent() {
            return block != null && block.getType() == Material.COBBLESTONE_WALL;
        }
    }
}
