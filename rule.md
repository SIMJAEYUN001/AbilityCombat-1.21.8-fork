---

## 체크리스트

새 능력 구현 시 아래를 확인하세요:

- [ ] `@EventHandler` 대신 `EventBridge` 사용
- [ ] 개별 `runTaskTimer` 대신 `registerTick()` 사용
- [ ] `world.getLivingEntities()` 대신 `LocationUtil` 또는 `NearbyEntityCache` 사용
- [ ] 직접 `spawnParticle()` 대신 `ParticleUtil` 사용
- [ ] 필요 시 `VectorPool` / `LocationPool` 활용
- [ ] `onDeactivate()` / `onDestroy()`에서 리소스 정리
- [ ] 능력 추가/수정 시 `ability.yml`에 변경 사항 기록
- [ ] **주변 탐색 시 `withValidTarget()` 또는 `isValidTarget()` 사용하여 관전자 제외**
- [ ] **`@AbilityManifest`의 `name`과 `abilities.yml`의 `name`이 정확히 일치하는지 확인**
- [ ] **플레이어 크기(SCALE) 변경 능력은 무적 해제 후(게임 시작 시점) 적용**
- [ ] **능력에서 사용하는 ArmorStand는 `AbilityCombat.markAbilityArmorStand()`로 태그하여 상호작용을 차단**

# AbilityCombat 능력 개발 최적화 가이드

이 문서는 새 능력을 개발하거나 기존 능력을 수정할 때 반드시 따라야 할 최적화 패턴들을 정리합니다.

---

## 1. 중앙 집중식 타이머 시스템 (`AbilityTickManager`)

### 사용법
- **절대 `BukkitScheduler.runTaskTimer()`를 직접 사용하지 마세요.**
- `AbilityBase`의 `registerTick()` / `unregisterTick()`을 사용하세요.

```java
@Override
protected void onActivate() {
    registerTick(); // 틱 루프에 등록
}

@Override
protected void onDeactivate() {
    unregisterTick(); // 틱 루프에서 해제
}

@Override
public void onTick(int tick) {
    if (tick % 20 == 0) { // 1초마다 실행
        // 로직
    }
}
```

### 주기 선택 가이드
| 주기 | 사용 예시 |
|------|-----------|
| `tick % 2` | 고빈도 VFX, 투사체 이동 |
| `tick % 8` | 이펙트 검사, 범위 스캔 |
| `tick % 20` | 초당 로직, UI 업데이트 |
| `tick % 40` | 저빈도 검사, 상태 확인 |

---

## 2. 이벤트 브릿지 (`EventBridge`)

### 사용법
- **`@EventHandler`를 능력 클래스에서 직접 사용하지 마세요.**
- `subscribeEvent(Event.class)`로 구독하고 `handleBridgeEvent()`에서 처리하세요.

```java
@Override
protected void onActivate() {
    subscribeEvent(EntityDamageByEntityEvent.class);
}

@Override
public void handleBridgeEvent(Event event) {
    if (event instanceof EntityDamageByEntityEvent e) {
        onDamageByEntity(e);
    }
}

private void onDamageByEntity(EntityDamageByEntityEvent event) {
    // 로직
}
```

### 지원되는 이벤트 목록
- `EntityDamageEvent`, `EntityDamageByEntityEvent`
- `EntityShootBowEvent`, `ProjectileHitEvent`
- `PlayerInteractEvent`, `PlayerInteractAtEntityEvent`
- `PlayerToggleSneakEvent`, `PlayerToggleFlightEvent`, `PlayerMoveEvent`, `PlayerDeathEvent`
- `PlayerTeleportEvent`, `PlayerItemHeldEvent`, `PlayerFishEvent`
- `BlockBreakEvent`, `BlockExplodeEvent`, `EntityExplodeEvent`
- `EntityRegainHealthEvent`, `EntityTargetLivingEntityEvent`

---

## 3. 주변 엔티티 캐싱 (`NearbyEntityCache`)

### 사용법
- **`world.getLivingEntities()`를 직접 반복문에서 호출하지 마세요.**
- `NearbyEntityCache` 또는 `LocationUtil.getNearbyLivingEntities()`를 사용하세요.

```java
private final NearbyEntityCache nearbyCache = new NearbyEntityCache();

// onTick 내에서
nearbyCache.getNearby(center, 8.0, e -> !e.equals(player), 10).forEach(target -> {
    // 로직
});
```

캐시 유효 틱: 10틱 (약 0.5초)

---

## 4. UI (`BossBar` & `Actionbar`)

### 사용법
- **`player.spigot().sendMessage()`를 매 틱 호출하지 마세요.**
- `BossBarManager`와 `ActionbarChannel`을 사용하세요.
### 쿨타임 표기 기본 규칙
- **기본은 바닐라 게이지 + 철괴 우클릭 시 채팅 메시지(`notifyCooldown`)**
- **액션바 쿨타임은 사용자 요청이 있을 때만 구현**

```java
// BossBar
getBossBarManager().update(player, "key", priority, title, progress, color, overlay);

// Actionbar
getActionbarChannel().update(player, "key", priority, message);
```

---

## 4-1. 철괴 바닐라 쿨타임 게이지

### 원칙
- **철괴 능력은 쿨타임 시작 직후 `applyIronCooldownIfEmpty`를 반드시 호출**
- **게이지는 표시용**이며, 실제 쿨타임 판정은 `Cooldown`으로 처리
- **게이지 충돌은 내부에서 자동으로 방지**됨 (다른 능력의 게이지는 덮어쓰지 않음)

### 사용법
```java
if (cooldown.isCooldown()) {
    notifyCooldown(cooldown);
    return false;
}

cooldown.start();
applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
```

### 적용 대상
- 철괴 우/좌클릭 등 **철괴로 발동되는 모든 능력**

---

## 5. 파티클 생성 (`ParticleUtil`)

### 사용법
- **`world.spawnParticle()`를 직접 호출하지 마세요.**
- `ParticleUtil.spawnParticle()`를 사용하세요.

```java
ParticleUtil.spawnParticle(world, Particle.FLAME, location, count, 0, 0, 0, 0, (Object) null, 2, 0);
```

### 최적화 파라미터
- `tickPeriod`: 파티클이 생성될 최소 틱 간격
- `maxDistance`: 플레이어와의 최대 거리

---

## 6. 객체 재사용 (`VectorPool`, `LocationPool`)

### 사용법
- **루프 내에서 `new Vector()`, `new Location()`을 남발하지 마세요.**
- `VectorPool.get()`, `LocationPool.get()`을 사용하세요.

```java
Vector direction = VectorPool.get(1, 0, 0);
Location loc = LocationPool.get(world, x, y, z);
```

> [!WARNING]
> 풀에서 가져온 객체는 다른 곳에 저장하면 안 됩니다. 단기 사용 후 바로 버려야 합니다.

---

## 7. 커스텀 엔티티 (`CustomEntity`)

### 사용법
- 복잡한 투사체는 `CustomEntity`를 상속하여 구현하세요.
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

---

## 8. 관전자 제외 (`isValidTarget`)

### 사용법
- **주변 엔티티를 탐색하거나 타겟팅할 때 반드시 관전자를 제외해야 합니다.**
- `LocationUtil.isValidTarget(LivingEntity)` 또는 `LocationUtil.withValidTarget(Predicate)` 사용

```java
// 방법 1: 직접 필터링
nearbyCache.getNearby(center, radius, e -> !e.equals(player) && LocationUtil.isValidTarget(e), 10);

// 방법 2: withValidTarget으로 기존 필터 래핑
LocationUtil.getNearbyLivingEntities(center, radius, 
    LocationUtil.withValidTarget(e -> !e.equals(player)));

// 방법 3: getEntityLookingAt에서 사용
LocationUtil.getEntityLookingAt(LivingEntity.class, player, range,
    e -> !e.equals(player) && LocationUtil.isValidTarget(e));
```

### 제외 대상
- `GameMode.SPECTATOR` 상태의 플레이어
- `Participant.isTargetable() == false`인 플레이어 (관전자로 전환됨)

> [!WARNING]
> 관전자가 능력의 영향을 받으면 게임 진행에 문제가 발생할 수 있습니다.
> 모든 주변 엔티티 탐색에서 반드시 이 헬퍼를 사용하세요.

---

## 9. 능력 이름 규칙 (`@AbilityManifest` ↔ `abilities.yml`)

### 중요
- **`@AbilityManifest`의 `name` 속성과 `abilities.yml`의 `name` 필드는 글자 하나까지 완벽하게 일치해야 합니다.**
- 이름이 불일치하면 해당 능력은 **추첨 풀에서 제외**되어 게임에 등장하지 않습니다.

### 올바른 형식
```
한글이름 (EnglishName)
```

- 괄호 안의 영어 이름에는 **공백이 없어야** 합니다.
- 클래스명과 영어 이름을 동일하게 유지하세요.

```java
// ✅ 올바른 예시
@AbilityManifest(name = "거인 학살자 (GiantSlayer)", ...)  // 클래스: GiantSlayer.java

// ❌ 잘못된 예시
@AbilityManifest(name = "거인 학살자 (Giant Slayer)", ...) // 공백 포함 → 추첨 제외
@AbilityManifest(name = "설인 (Yeti)", ...)                // YAML과 불일치 → 추첨 제외
```

### 체크 방법
새 능력 추가 시:
1. `@AbilityManifest`의 `name` 값 확인
2. `abilities.yml`에 동일한 `name` 값으로 등록
3. 영어 이름에 공백이 없는지 확인

> [!CAUTION]
> 이름 불일치는 컴파일 에러를 발생시키지 않아 발견이 어렵습니다.
> 능력이 추첨에 나오지 않으면 가장 먼저 이름 일치 여부를 확인하세요.

