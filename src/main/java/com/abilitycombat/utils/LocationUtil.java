package com.abilitycombat.utils;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.game.Participant;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * 위치 관련 유틸리티
 */
public class LocationUtil {

    /**
     * 대상이 유효한 타겟인지 확인합니다.
     * 관전자(GameMode.SPECTATOR) 또는 Participant.isTargetable() == false인 경우 제외됩니다.
     * 
     * @param entity 확인할 엔티티
     * @return 유효한 타겟이면 true
     */
    public static boolean isValidTarget(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof Player player) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                return false;
            }
            // GameManager의 Participant 확인
            var gameManager = AbilityCombat.getPlugin().getGameManager();
            if (gameManager != null) {
                Participant participant = gameManager.getParticipant(player.getUniqueId());
                if (participant != null && !participant.isTargetable()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isValidTarget(LivingEntity source, LivingEntity entity) {
        if (!isValidTarget(entity)) {
            return false;
        }
        if (source != null && source.equals(entity)) {
            return false;
        }
        if (source instanceof Player sourcePlayer && entity instanceof Player targetPlayer) {
            var gameManager = AbilityCombat.getPlugin().getGameManager();
            if (gameManager != null && gameManager.areTeammates(sourcePlayer, targetPlayer)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 대상 필터에 관전자 제외를 추가한 Predicate를 반환합니다.
     * 
     * @param basePredicate 기존 필터 (null 가능)
     * @return 관전자 제외가 추가된 필터
     */
    public static Predicate<LivingEntity> withValidTarget(Predicate<LivingEntity> basePredicate) {
        if (basePredicate == null) {
            return LocationUtil::isValidTarget;
        }
        return entity -> isValidTarget(entity) && basePredicate.test(entity);
    }

    public static Predicate<LivingEntity> withValidTarget(LivingEntity source,
            Predicate<LivingEntity> basePredicate) {
        if (basePredicate == null) {
            return entity -> isValidTarget(source, entity);
        }
        return entity -> isValidTarget(source, entity) && basePredicate.test(entity);
    }

    /**
     * 범위 내 엔티티 가져오기
     */
    public static <T extends Entity> Collection<T> getNearbyEntities(Class<T> entityClass, Location center,
            double radius, Predicate<T> predicate) {
        return getNearbyEntities(entityClass, center, radius, radius, radius, predicate);
    }

    /**
     * 범위 내 엔티티 가져오기 (X, Y, Z 별도 지정)
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> Collection<T> getNearbyEntities(Class<T> entityClass, Location center, double x,
            double y, double z, Predicate<T> predicate) {
        List<T> result = new ArrayList<>();
        World world = center.getWorld();
        if (world == null)
            return result;

        for (Entity entity : world.getNearbyEntities(center, x, y, z)) {
            if (entityClass.isInstance(entity)) {
                T target = (T) entity;
                if (predicate == null || predicate.test(target)) {
                    result.add(target);
                }
            }
        }
        return result;
    }

    /**
     * 범위 내 LivingEntity 가져오기
     */
    public static Collection<LivingEntity> getNearbyLivingEntities(Location center, double radius,
            Predicate<LivingEntity> predicate) {
        return getNearbyEntities(LivingEntity.class, center, radius, withValidTarget(predicate));
    }

    public static Collection<LivingEntity> getNearbyLivingEntities(Location center, double radius, LivingEntity source,
            Predicate<LivingEntity> predicate) {
        return getNearbyEntities(LivingEntity.class, center, radius, withValidTarget(source, predicate));
    }

    /**
     * 범위 내 플레이어 가져오기
     */
    public static Collection<Player> getNearbyPlayers(Location center, double radius, Predicate<Player> predicate) {
        Predicate<Player> playerFilter = p -> isValidTarget(p) && (predicate == null || predicate.test(p));
        return getNearbyEntities(Player.class, center, radius, playerFilter);
    }

    public static Collection<Player> getNearbyPlayers(Location center, double radius, LivingEntity source,
            Predicate<Player> predicate) {
        Predicate<Player> playerFilter = p -> isValidTarget(source, p) && (predicate == null || predicate.test(p));
        return getNearbyEntities(Player.class, center, radius, playerFilter);
    }

    /**
     * 가장 가까운 엔티티 가져오기
     */
    public static <T extends Entity> T getNearestEntity(Class<T> entityClass, Location center, double radius,
            Predicate<T> predicate) {
        T nearest = null;
        double minDistanceSquared = Double.MAX_VALUE;

        Predicate<T> finalPredicate = predicate;
        if (LivingEntity.class.isAssignableFrom(entityClass)) {
            finalPredicate = entity -> {
                if (entity instanceof LivingEntity le && !isValidTarget(le))
                    return false;
                return predicate == null || predicate.test(entity);
            };
        }

        for (T entity : getNearbyEntities(entityClass, center, radius, finalPredicate)) {
            double distanceSquared = entity.getLocation().distanceSquared(center);
            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                nearest = entity;
            }
        }
        return nearest;
    }

    /**
     * 플레이어가 바라보는 방향의 엔티티 가져오기
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> T getEntityLookingAt(Class<T> entityClass, Player player, double maxDistance,
            Predicate<T> predicate) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection().normalize();

        Predicate<T> finalPredicate = predicate;
        if (LivingEntity.class.isAssignableFrom(entityClass)) {
            finalPredicate = entity -> {
                if (entity instanceof LivingEntity le && !isValidTarget(le))
                    return false;
                return predicate == null || predicate.test(entity);
            };
        }

        for (double d = 0.5; d <= maxDistance; d += 0.5) {
            Location checkLoc = eyeLocation.clone().add(direction.clone().multiply(d));

            for (Entity entity : checkLoc.getWorld().getNearbyEntities(checkLoc, 0.5, 0.5, 0.5)) {
                if (entityClass.isInstance(entity) && !entity.equals(player)) {
                    T target = (T) entity;
                    if (finalPredicate == null || finalPredicate.test(target)) {
                        return target;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 두 위치 간 거리의 제곱
     */
    public static double distanceSquared(Location loc1, Location loc2) {
        if (loc1.getWorld() != loc2.getWorld())
            return Double.MAX_VALUE;
        return loc1.distanceSquared(loc2);
    }

    /**
     * 두 위치가 같은 월드에 있고 범위 내에 있는지 확인
     */
    public static boolean isInRange(Location loc1, Location loc2, double range) {
        if (loc1.getWorld() != loc2.getWorld())
            return false;
        return loc1.distanceSquared(loc2) <= range * range;
    }

    /**
     * 원 안에 있는지 확인
     */
    public static boolean isInCircle(Location center, Location check, double radius) {
        if (center.getWorld() != check.getWorld())
            return false;
        double dx = center.getX() - check.getX();
        double dz = center.getZ() - check.getZ();
        return (dx * dx + dz * dz) <= radius * radius;
    }

    /**
     * 바닥 Y 좌표 찾기
     */
    public static int getFloorY(World world, int x, int z, int startY) {
        for (int y = startY; y > world.getMinHeight(); y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return y + 1;
            }
        }
        return world.getMinHeight();
    }
}
