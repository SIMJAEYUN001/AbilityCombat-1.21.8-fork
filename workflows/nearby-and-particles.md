# 주변 탐색 · 파티클 · 객체풀 · 커스텀 엔티티

## 주변 엔티티 캐싱 (`NearbyEntityCache`)
- **`world.getLivingEntities()`를 직접 반복문에서 호출하지 마세요.**
- `NearbyEntityCache` 또는 `LocationUtil.getNearbyLivingEntities()`를 사용합니다.

```java
private final NearbyEntityCache nearbyCache = new NearbyEntityCache();

// onTick 내에서
nearbyCache.getNearby(center, 8.0, e -> !e.equals(player), 10).forEach(target -> {
    // 로직
});
```

캐시 유효 틱: 10틱 (약 0.5초). 관전자/팀 제외 필터는 [spectator-and-teams.md](spectator-and-teams.md) 참고.

---

## 파티클 생성 (`ParticleUtil`)
- **`world.spawnParticle()`를 직접 호출하지 마세요.**
- `ParticleUtil.spawnParticle()`를 사용합니다.

```java
ParticleUtil.spawnParticle(world, Particle.FLAME, location, count, 0, 0, 0, 0, (Object) null, 2, 0);
```

- `tickPeriod`: 파티클이 생성될 최소 틱 간격
- `maxDistance`: 플레이어와의 최대 거리

---

## 객체 재사용 (`VectorPool`, `LocationPool`)
- **루프 내에서 `new Vector()`, `new Location()`을 남발하지 마세요.**
- `VectorPool.get()`, `LocationPool.get()`을 사용합니다.

```java
Vector direction = VectorPool.get(1, 0, 0);
Location loc = LocationPool.get(world, x, y, z);
```

> [!WARNING]
> 풀에서 가져온 객체는 다른 곳에 저장하면 안 됩니다. 단기 사용 후 바로 버려야 합니다.

---

## 커스텀 엔티티 (`CustomEntity`)
- 복잡한 투사체는 `CustomEntity`를 상속하여 구현합니다.
- `onTick()`, `onHitEntity()`, `onHitBlock()` 메서드를 오버라이드합니다.

```java
public class MyProjectile extends CustomEntity implements Deflectable {
    @Override
    protected boolean onHitEntity(LivingEntity entity, Location hitloc) {
        entity.damage(5.0, source);
        return true; // 소멸
    }
}
```

- 능력에서 사용하는 ArmorStand는 `AbilityCombat.markAbilityArmorStand()`로 태그하여 상호작용을 차단합니다.
