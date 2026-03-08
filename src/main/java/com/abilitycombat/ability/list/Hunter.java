package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@AbilityManifest(name = "사냥꾼 (Hunter)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.HUMAN, explain = {
        "§e§l[패시브 - 사냥꾼의 본능]",
        "§7근접 공격(칼, 도끼) 피해가 §f40%§7 감소합니다.",
        "§7석궁 사용 시 §e자동 장전§7됩니다.",
        "§7석궁 화살 적중 시 §b정조준§7 쿨타임이 §f5초§7 감소합니다.",
        "",
        "§e§l[석궁 좌클릭 - 정조준]§f §8(쿨타임: 50초)",
        "§7다음 발사체를 §c폭발 화살§7로 강화합니다.",
        "§7적중 1초 후 §f3칸§7 반경에 §c18의 광역 피해§7를 입힙니다.",
        "",
        "§e§l[철괴 우클릭 - 추적]§f §8(쿨타임: 45초)",
        "§7주변 §f30칸§7을 탐색하여 가장 가까운 플레이어에게",
        "§e발광§7 효과와 받는 피해 §c10% 증가§7 디버프를 §f15초§7간 부여합니다."
}, summarize = {
        "§7패시브§f: 근접 피해 40%↓, 석궁 자동 장전",
        "§7석궁 좌클릭§f: 폭발 화살 (18 광역, 50초)",
        "§7철괴 우클릭§f: 추적 (발광 + 피해↑, 45초)"
})
public class Hunter extends AbilityBase implements ActiveHandler {

    // 상수
    private static final int AIM_COOLDOWN_SECONDS = 50;
    private static final int TRACK_COOLDOWN_SECONDS = 45;
    private static final int TRACK_DURATION_SECONDS = 15;
    private static final double TRACK_RANGE = 30.0;
    private static final double EXPLOSION_RADIUS = 3.0;
    private static final double EXPLOSION_DAMAGE = 18.0;
    private static final int EXPLOSION_DELAY_TICKS = 20; // 1초
    private static final double MELEE_DAMAGE_REDUCTION = 0.6; // 40% 감소
    private static final double TRACK_DAMAGE_INCREASE = 1.1; // 10% 증가
    private static final int AIM_COOLDOWN_REDUCTION = 5;
    private static final String HUNTER_ENHANCED_KEY_NAME = "hunter_enhanced";

    // 근접 무기 타입
    private static final Set<Material> MELEE_WEAPONS = Set.of(
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
            Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE);

    // 쿨다운
    private final ActionbarCooldown aimCooldown = new ActionbarCooldown(AIM_COOLDOWN_SECONDS);
    private final Cooldown trackCooldown = new Cooldown(TRACK_COOLDOWN_SECONDS);

    // 상태
    private boolean nextShotEnhanced = false;
    private final Map<UUID, Long> trackedTargets = new HashMap<>(); // UUID -> 만료 시간
    private int lastAimTick = -1;

    public Hunter(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityDamageByEntityEvent.class);
        subscribeEvent(EntityShootBowEvent.class);
        subscribeEvent(ProjectileHitEvent.class);
        subscribeEvent(PlayerInteractEvent.class);
        subscribeEvent(PlayerAnimationEvent.class);
        giveCrossbowAndArrows();
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        trackedTargets.clear();
    }

    @Override
    protected void onDestroy() {
        trackedTargets.clear();
    }

    /**
     * 석궁과 화살 지급
     */
    private void giveCrossbowAndArrows() {
        Player player = getPlayer();
        if (player == null)
            return;

        // 인챈트 없는 석궁 지급
        ItemStack crossbow = new ItemStack(Material.CROSSBOW, 1);
        player.getInventory().addItem(crossbow);
        player.getInventory().addItem(new ItemStack(Material.ARROW, 128));
    }

    // =============== ActiveHandler ===============

    @Override
    public boolean activeSkill(Material material, ClickType clickType) {
        // 철괴 우클릭 - 추적
        if (material == Material.IRON_INGOT && clickType == ClickType.RIGHT_CLICK) {
            return activateTrack();
        }
        return false;
    }

    // =============== 이벤트 처리 ===============

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent e) {
            onDamageByEntity(e);
        } else if (event instanceof EntityShootBowEvent e) {
            onShootBow(e);
        } else if (event instanceof ProjectileHitEvent e) {
            onProjectileHit(e);
        } else if (event instanceof PlayerInteractEvent e) {
            onPlayerInteract(e);
        } else if (event instanceof PlayerAnimationEvent e) {
            onPlayerAnimation(e);
        }
    }

    /**
     * 석궁 좌클릭 또는 석궁으로 타격 - 정조준 활성화
     */
    private void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getPlayer().equals(getPlayer()))
            return;
        if (Bukkit.getCurrentTick() == lastAimTick)
            return;

        Player player = getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 석궁인지 확인
        if (item.getType() != Material.CROSSBOW)
            return;

        // 좌클릭 (AIR, BLOCK 모두) 확인
        String action = event.getAction().name();
        if (!action.contains("LEFT_CLICK"))
            return;

        lastAimTick = Bukkit.getCurrentTick();
        activateAim(player, item);
    }

    /**
     * 허공 좌클릭 누락 보완 - 팔 휘두름 이벤트로 처리
     */
    private void onPlayerAnimation(PlayerAnimationEvent event) {
        if (!event.getPlayer().equals(getPlayer()))
            return;
        if (Bukkit.getCurrentTick() == lastAimTick)
            return;

        Player player = getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.CROSSBOW)
            return;

        lastAimTick = Bukkit.getCurrentTick();
        activateAim(player, item);
    }

    /**
     * 정조준 활성화 공통 로직
     */
    private void activateAim(Player player, ItemStack crossbow) {
        // 쿨타임 확인
        if (aimCooldown.isCooldown()) {
            notifyCooldown(aimCooldown);
            return;
        }

        // 이미 강화 상태면 무시
        if (nextShotEnhanced) {
            return;
        }

        // 석궁에 화살이 장전되어 있는지 확인
        if (!(crossbow.getItemMeta() instanceof CrossbowMeta crossbowMeta))
            return;
        if (!crossbowMeta.hasChargedProjectiles()) {
            return;
        }

        // 장전된 화살을 폭죽으로 교체
        crossbowMeta.setChargedProjectiles(java.util.List.of(new ItemStack(Material.FIREWORK_ROCKET)));
        crossbow.setItemMeta(crossbowMeta);

        // 정조준 활성화
        nextShotEnhanced = true;
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.5f);

        // 이펙트
        ParticleUtil.spawnParticle(player.getWorld(), Particle.ENCHANT,
                player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.5, null, 2, 0);
    }

    /**
     * 석궁 발사 처리 - 즉시 장전 & 정조준 시 폭죽 태깅
     */
    private void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        if (!player.equals(getPlayer()))
            return;

        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() != Material.CROSSBOW)
            return;

        // 정조준 상태에서 폭죽 발사 시 태깅
        if (nextShotEnhanced && event.getProjectile() instanceof Firework firework) {
            nextShotEnhanced = false;
            aimCooldown.start();

            // PersistentDataContainer로 강화된 폭죽 표시
            NamespacedKey key = new NamespacedKey(AbilityCombat.getPlugin(), HUNTER_ENHANCED_KEY_NAME);
            firework.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

            // 체공시간 최대로 설정 (3 = 약 3초)
            FireworkMeta fwMeta = firework.getFireworkMeta();
            fwMeta.setPower(3);
            firework.setFireworkMeta(fwMeta);
        }

        // 석궁 자동 재장전 (6틱 딜레이)
        AbilityCombat.getPlugin().getServer().getScheduler().runTaskLater(
                AbilityCombat.getPlugin(),
                () -> reloadCrossbow(player, bow),
                6L);
    }

    /**
     * 석궁 자동 재장전
     */
    private void reloadCrossbow(Player player, ItemStack crossbow) {
        if (crossbow == null || crossbow.getType() != Material.CROSSBOW)
            return;
        if (!player.getInventory().contains(Material.ARROW))
            return;

        CrossbowMeta meta = (CrossbowMeta) crossbow.getItemMeta();
        if (meta == null)
            return;

        // 이미 장전되어 있으면 무시
        if (meta.hasChargedProjectiles())
            return;

        // 화살 장전
        meta.addChargedProjectile(new ItemStack(Material.ARROW));
        crossbow.setItemMeta(meta);

        // 인벤토리에서 화살 1개 소모
        player.getInventory().removeItem(new ItemStack(Material.ARROW, 1));
        player.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.5f, 1.5f);
    }

    /**
     * 투사체 적중 처리
     */
    private void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter))
            return;
        if (!shooter.equals(getPlayer()))
            return;

        // 강화된 폭죽 적중
        NamespacedKey key = new NamespacedKey(AbilityCombat.getPlugin(), HUNTER_ENHANCED_KEY_NAME);
        if (projectile instanceof Firework firework &&
                firework.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {

            // 플레이어 적중 시: 해당 플레이어를 추적하여 1초 후 그 위치에서 폭발
            if (event.getHitEntity() instanceof LivingEntity hitTarget) {
                firework.remove();
                AbilityCombat.getPlugin().getServer().getScheduler().runTaskLater(
                        AbilityCombat.getPlugin(),
                        () -> triggerExplosion(hitTarget.getLocation(), shooter),
                        EXPLOSION_DELAY_TICKS);
            } else {
                // 블록/지형 적중 시: 폭죽 엔티티를 그 자리에 유지하여 폭발 지점 표시
                Location hitLoc = firework.getLocation();

                // 폭죽 움직임 정지
                firework.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                firework.setGravity(false);

                // 경고 파티클 표시
                AbilityCombat.getPlugin().getServer().getScheduler().runTaskLater(
                        AbilityCombat.getPlugin(),
                        () -> {
                            firework.remove();
                            triggerExplosion(hitLoc, shooter);
                        },
                        EXPLOSION_DELAY_TICKS);
            }
            return;
        }

        // 일반 석궁 화살 적중 - 정조준 쿨타임 감소
        if (projectile instanceof Arrow && event.getHitEntity() instanceof LivingEntity) {
            if (aimCooldown.isCooldown()) {
                int newCount = Math.max(0, aimCooldown.getCount() - AIM_COOLDOWN_REDUCTION);
                aimCooldown.setCount(newCount);

                if (newCount <= 0) {
                    aimCooldown.stop(false);
                }
            }
        }
    }

    /**
     * 폭발 화살 광역 피해
     */
    private void triggerExplosion(Location center, Player shooter) {
        // 폭발 이펙트 - 폭죽 효과
        ParticleUtil.spawnParticle(center.getWorld(), Particle.EXPLOSION_EMITTER,
                center, 1, 0, 0, 0, 0, null, 1, 0);
        ParticleUtil.spawnParticle(center.getWorld(), Particle.EXPLOSION,
                center, 5, 0.5, 0.5, 0.5, 0, null, 1, 0);

        // 불꽃 효과
        ParticleUtil.spawnParticle(center.getWorld(), Particle.FLAME,
                center, 80, 1.2, 1.2, 1.2, 0.15, null, 1, 0);

        // 폭발음
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
        center.getWorld().playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f, 1.0f);
        center.getWorld().playSound(center, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.5f, 0.8f);

        // 광역 피해
        for (LivingEntity target : LocationUtil.getNearbyLivingEntities(center, EXPLOSION_RADIUS, shooter,
                e -> !e.equals(shooter))) {
            target.damage(EXPLOSION_DAMAGE, shooter);
            target.setVelocity(target.getLocation().toVector()
                    .subtract(center.toVector()).normalize().multiply(0.8).setY(0.4));
        }
    }

    /**
     * 피해 이벤트 처리 - 근접 피해 감소 & 추적 대상 피해 증가 & 석궁 타격 시 정조준
     */
    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player player = getPlayer();

        // 플레이어가 피격 - 근접 피해 40% 감소
        if (event.getEntity().equals(player)) {
            if (event.getDamager() instanceof Player attacker) {
                ItemStack weapon = attacker.getInventory().getItemInMainHand();
                if (MELEE_WEAPONS.contains(weapon.getType())) {
                    event.setDamage(event.getDamage() * MELEE_DAMAGE_REDUCTION);
                }
            }
        }

        // 플레이어가 석궁으로 타격 - 정조준 활성화
        if (event.getDamager().equals(player)) {
            ItemStack weapon = player.getInventory().getItemInMainHand();
            if (weapon.getType() == Material.CROSSBOW) {
                activateAim(player, weapon);
            }
        }

        // 플레이어가 공격 - 추적 대상에게 추가 피해
        if (event.getDamager().equals(player) ||
                (event.getDamager() instanceof Projectile proj && proj.getShooter().equals(player))) {

            if (event.getEntity() instanceof Player victim) {
                UUID victimUUID = victim.getUniqueId();
                Long expireTime = trackedTargets.get(victimUUID);

                if (expireTime != null && System.currentTimeMillis() < expireTime) {
                    event.setDamage(event.getDamage() * TRACK_DAMAGE_INCREASE);
                }
            }
        }
    }

    // =============== 추적 스킬 ===============

    /**
     * 추적 스킬 활성화
     */
    private boolean activateTrack() {
        if (trackCooldown.isCooldown()) {
            notifyCooldown(trackCooldown);
            return false;
        }

        Player player = getPlayer();

        // 30칸 내 가장 가까운 플레이어 찾기
        Player target = LocationUtil.getNearestEntity(Player.class, player.getLocation(), TRACK_RANGE,
                p -> !p.equals(player) && LocationUtil.isValidTarget(getPlayer(), p));

        if (target == null) {
            player.sendMessage("§c주변에 추적할 대상이 없습니다.");
            return false;
        }

        // 발광 효과 부여
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                TRACK_DURATION_SECONDS * 20, 0, false, false));

        // 추적 대상 등록 (피해 증가 디버프)
        long expireTime = System.currentTimeMillis() + (TRACK_DURATION_SECONDS * 1000L);
        trackedTargets.put(target.getUniqueId(), expireTime);

        // 이펙트 & 메시지
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1.0f, 1.5f);
        ParticleUtil.spawnParticle(player.getWorld(), Particle.END_ROD,
                player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1, null, 2, 0);

        player.sendMessage("§e§l[추적]§f §c" + target.getName() + "§f을(를) 추적합니다! (§e" + TRACK_DURATION_SECONDS + "초§f)");
        target.sendMessage("§c§l[경고]§f 누군가에게 추적당하고 있습니다!");

        trackCooldown.start();
        applyIronCooldownIfEmpty(TRACK_COOLDOWN_SECONDS);
        return true;
    }

    // =============== 틱 처리 ===============

    @Override
    public void onTick(int tick) {
        // 만료된 추적 대상 정리 (20틱마다)
        if (tick % 20 == 0) {
            long now = System.currentTimeMillis();
            trackedTargets.entrySet().removeIf(entry -> entry.getValue() < now);
        }
    }
}
