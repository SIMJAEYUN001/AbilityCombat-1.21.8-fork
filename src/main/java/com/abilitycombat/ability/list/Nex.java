package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

@AbilityManifest(name = "넥스 (Nex)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.GOD, explain = {
        "§e§l[철괴 우클릭 - 죽음의 강림]§f §8(쿨타임: 30초)",
        "§7공중으로 §f높이 솟아오른 뒤§7,",
        "§7바라보는 방향으로 §c빠르게 돌진§7하며 §4내려찍습니다§7.",
        "",
        "§7착지 지점 주변 §f5칸§7 이내의 생명체에게",
        "§c20의 피해§7를 주고 §6밀쳐냅니다§7.",
        "",
        "§7착지 시 §8폭발 이펙트§7가 발생합니다.",
        "§7스킬 사용 중에는 낙하 피해를 받지 않습니다."
}, summarize = {
        "§7철괴 우클릭§f: 공중 도약 → 돌진 → 내려찍기"
})
public class Nex extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 30;
    private static final double IMPACT_RADIUS = 5.0;
    private static final double IMPACT_DAMAGE = 20.0;
    private static final double LEAP_POWER = 3;
    private static final double DIVE_SPEED = 3;
    private static final float EXPLOSION_POWER = 3.0f;
    private static final boolean EXPLOSION_SET_FIRE = false;
    private static final boolean EXPLOSION_BREAK_BLOCKS = true;
    private static final int NO_DAMAGE_EXPLOSION_TICKS = 2;
    private static final double KNOCKBACK_HORIZONTAL = 1.2;
    private static final double KNOCKBACK_Y = 0.65;

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private boolean noFallDamage = false;
    private NexMode mode = NexMode.IDLE;
    private int remainingRuns = 0;
    private Vector diveVelocity = null;
    private int noDamageExplosionUntilTick = -1;
    private Location noDamageExplosionCenter;
    private double noDamageExplosionRadiusSquared;

    private enum NexMode {
        IDLE, LEAPING, DIVING
    }

    public Nex(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        mode = NexMode.IDLE;
        noFallDamage = false;
        diveVelocity = null;
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
        if (mode != NexMode.IDLE) {
            return false;
        }
        startLeap();
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageEvent) {
            onFallDamage((EntityDamageEvent) event);
        }
    }

    private void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (!event.getEntity().equals(getPlayer())) {
                return;
            }
            // 스킬 사용 중이거나 noFallDamage 플래그가 켜져 있으면 낙하 데미지 무효
            if (noFallDamage || mode != NexMode.IDLE) {
                event.setCancelled(true);
            }
            return;
        }

        // 넥스 착지 폭발은 블럭 파괴/넉백/이벤트는 유지하되 데미지는 무효 처리
        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            int tick = Bukkit.getCurrentTick();
            if (tick > noDamageExplosionUntilTick) {
                return;
            }
            if (noDamageExplosionCenter == null) {
                return;
            }
            if (event.getEntity().getWorld() != noDamageExplosionCenter.getWorld()) {
                return;
            }
            if (event.getEntity().getLocation().distanceSquared(noDamageExplosionCenter) <= noDamageExplosionRadiusSquared) {
                event.setCancelled(true);
            }
        }
    }

    private void startLeap() {
        Player player = getPlayer();
        noFallDamage = true;
        // 위로만 도약 (앞으로 X)
        player.setVelocity(new Vector(0, LEAP_POWER, 0));
        mode = NexMode.LEAPING;
        remainingRuns = 10;
        diveVelocity = null;
        registerTick();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
    }

    private void startDive() {
        Player player = getPlayer();
        mode = NexMode.DIVING;
        remainingRuns = 60;

        // 바라보는 방향으로 돌진 + 아래로
        Vector direction = player.getLocation().getDirection().normalize();
        direction.multiply(DIVE_SPEED).setY(-2.0);
        diveVelocity = direction;
        player.setVelocity(diveVelocity);
    }

    private void impact() {
        if (mode != NexMode.DIVING) {
            return;
        }
        mode = NexMode.IDLE;
        unregisterTick();
        diveVelocity = null;

        Player player = getPlayer();
        Location center = player.getLocation();
        World world = center.getWorld();

        if (world == null) {
            return;
        }

        // 낙하 데미지 무효를 잠시 후 해제 (착지 직후 이벤트 처리를 위해)
        Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> noFallDamage = false, 5L);

        // 폭발 파티클 (실제 폭발과 별개로 연출 강화)
        ParticleUtil.spawnParticle(world, Particle.SMOKE, center, 30, 2, 0.5, 2, 0.1, 1, 0);
        ParticleUtil.spawnParticle(world, Particle.CLOUD, center, 20, 1.5, 0.3, 1.5, 0.05, 1, 0);

        // 블록 튀어오르는 이펙트 시작
        spawnFallingBlocks(center, 1);

        // 데미지 없는 폭발 (블럭 파괴/이벤트/넉백은 발생)
        markNoDamageExplosion(center, EXPLOSION_POWER);
        world.createExplosion(center, EXPLOSION_POWER, EXPLOSION_SET_FIRE, EXPLOSION_BREAK_BLOCKS, player);

        // 주변 적에게 피해 + 넉백
        for (LivingEntity entity : LocationUtil.getNearbyLivingEntities(center, IMPACT_RADIUS, player, null)) {
            if (entity.equals(player)) {
                continue;
            }
            entity.damage(IMPACT_DAMAGE, player);
            Vector push = entity.getLocation().toVector().subtract(center.toVector()).normalize()
                    .multiply(KNOCKBACK_HORIZONTAL);
            push.setY(KNOCKBACK_Y);
            entity.setVelocity(push);

            // 피격된 플레이어에게도 폭발음
            if (entity instanceof Player target) {
                target.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
            }
        }
    }

    /**
     * 원형으로 확산되는 블록 튀어오르는 이펙트
     */
    private void spawnFallingBlocks(Location center, int wave) {
        if (wave > 5) {
            return;
        }

        World world = center.getWorld();
        if (world == null) {
            return;
        }

        int baseX = center.getBlockX();
        int baseY = center.getBlockY();
        int baseZ = center.getBlockZ();

        // 원형 범위의 블록들 튀어오르게
        for (int x = -wave; x <= wave; x++) {
            for (int z = -wave; z <= wave; z++) {
                // 원형 테두리만 (이전 wave 제외)
                int distSq = x * x + z * z;
                if (distSq > wave * wave || distSq <= (wave - 1) * (wave - 1)) {
                    continue;
                }

                // 해당 위치의 지표면 블록 찾기
                Block block = world.getBlockAt(baseX + x, baseY - 1, baseZ + z);
                if (block.getType().isAir()) {
                    block = block.getRelative(BlockFace.DOWN);
                }
                if (block.getType().isAir() || !block.getType().isSolid()) {
                    continue;
                }

                // FallingBlock 생성
                BlockData blockData = block.getBlockData().clone();
                Location spawnLoc = block.getLocation().add(0.5, 1.0, 0.5);

                FallingBlock fallingBlock = world.spawn(spawnLoc, FallingBlock.class, fb -> {
                    fb.setBlockData(blockData);
                    fb.setDropItem(false);
                    fb.setHurtEntities(false);
                    Vector velocity = spawnLoc.toVector().subtract(center.toVector()).normalize().multiply(0.15);
                    velocity.setY(0.3 + Math.random() * 0.2);
                    fb.setVelocity(velocity);
                });

                // 짧은 시간 후 제거 (땅에 안착하지 않도록)
                Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), fallingBlock::remove, 20L);
            }
        }

        // 다음 wave 예약 (4틱 후)
        Bukkit.getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> spawnFallingBlocks(center, wave + 1), 4L);
    }

    private void markNoDamageExplosion(Location center, float power) {
        noDamageExplosionCenter = center.clone();
        noDamageExplosionUntilTick = Bukkit.getCurrentTick() + Math.max(1, NO_DAMAGE_EXPLOSION_TICKS);
        double radius = Math.max(0.5, power * 2.0);
        noDamageExplosionRadiusSquared = radius * radius;
    }

    private boolean isOnGround(Player player) {
        Block blockHere = player.getLocation().getBlock();
        Block blockBelow = blockHere.getRelative(BlockFace.DOWN);
        return !blockHere.getType().isAir() || !blockBelow.getType().isAir();
    }

    @Override
    public void onTick(int tick) {
        if (isDestroyed() || mode == NexMode.IDLE) {
            unregisterTick();
            return;
        }

        if (mode == NexMode.DIVING) {
            Player player = getPlayer();
            if (player == null) {
                mode = NexMode.IDLE;
                unregisterTick();
                return;
            }
            if (isOnGround(player)) {
                impact();
                return;
            }
            if (diveVelocity != null) {
                player.setVelocity(diveVelocity);
            }
        }

        if (tick % 4 == 0) {
            processModeLogic();
        }
    }

    private void processModeLogic() {
        Player player = getPlayer();
        if (player == null) {
            mode = NexMode.IDLE;
            return;
        }

        if (mode == NexMode.LEAPING) {
            // Leap logic
            Location loc = player.getLocation().add(0, 0.5, 0);
            ParticleUtil.spawnParticle(player.getWorld(), Particle.LARGE_SMOKE, loc, 5, 0.5, 0.3, 0.5, 0.02, 2, 0);

            remainingRuns--;
            if (remainingRuns <= 0) {
                startDive();
            }
        } else if (mode == NexMode.DIVING) {
            // Dive logic
            ParticleUtil.spawnParticle(player.getWorld(), Particle.LARGE_SMOKE, player.getLocation(), 3, 0.2, 0.2, 0.2,
                    0.01, 2, 0);

            if (isOnGround(player)) {
                impact();
            } else {
                remainingRuns--;
                if (remainingRuns <= 0) {
                    impact();
                }
            }
        }
    }
}
