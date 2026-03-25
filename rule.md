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
- [ ] **팀전 영향을 받는 타게팅/범위 탐색은 source-aware `withValidTarget(getPlayer(), ...)` 또는 `isValidTarget(getPlayer(), target)` 사용**
- [ ] **직접 `damage()`, `setVelocity()`, 강제 이동, 디버프 부여, 폭발 피해를 줄 때 팀원 제외가 필요한지 반드시 확인**
- [ ] **바닐라 `createExplosion()`은 팀 필터가 불가능하므로 팀전 안전 처리가 필요하면 수동 피해/넉백 처리 사용**
- [ ] **팀전 UI는 적에게 정보가 보이지 않도록 viewer별 표시 여부를 확인 (`showEntity` / `hideEntity`)**
- [ ] **`@AbilityManifest`의 `name`과 `abilities.yml`의 `name`이 정확히 일치하는지 확인**
- [ ] **플레이어 크기(SCALE) 변경 능력은 무적 해제 후(게임 시작 시점) 적용**
- [ ] **능력에서 사용하는 ArmorStand는 `AbilityCombat.markAbilityArmorStand()`로 태그하여 상호작용을 차단**
- [ ] **은신/영체화/잠입 능력은 `storedInvisible`/`storedCollidable` 저장-복원 방식 대신 강제 on/off 또는 별도 상태 관리자 사용**
- [ ] **대시/임시 투명화와 겹칠 수 있는 능력은 은신 시작 전에 반드시 `SprintHudService.cancelDashState(player)`를 먼저 호출**
- [ ] **`hidePlayer`/`showPlayer`를 쓰는 은신은 다른 은신 상태(예: 헤르밋)와 충돌하지 않도록 "누가 숨겼는지"를 고려**

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

## 4-2. 은신 / 투명화 능력 주의사항

### 원칙
- **대시, 스턴, 강제 시각 효과와 겹칠 수 있는 은신은 현재 invis/collidable 상태를 저장한 뒤 복원하지 마세요.**
- **기본은 강제 on/off** 입니다.
- 정말 중첩 상태 보존이 필요하면 `storedInvisible` 같은 단순 bool이 아니라 **상태 관리자(reference/owner 기반)** 로 관리하세요.

### 이유
- 대시, 테스트 대시, 기타 임시 효과도 `setInvisible(true)`, `setCollidable(false)`를 사용할 수 있습니다.
- 이 상태를 은신 능력이 "원래 상태"로 저장하면 종료 시 다시 `true/false`를 복원해서 **무한 은신 / 충돌 비활성화**가 남습니다.

### 필수 규칙
- 은신 시작 전: `SprintHudService.cancelDashState(player)` 먼저 호출
- 은신 시작: `player.setInvisible(true)`, `player.setCollidable(false)` 강제 적용
- 은신 종료: `player.setInvisible(false)`, `player.setCollidable(true)` 강제 적용
- `hidePlayer` / `showPlayer` 사용 시: 다른 능력의 숨김 상태와 충돌하지 않는지 확인

### 현재 동일 위험 패턴 후보
- `Liberator`
- `Soul`
- `SoulEncroach`
- `Scarecrow`
- `Stalker`
- `Hermit`
- `Ghost`

### 부분 위험 후보
- `Bellum`: `collidable`만 저장/복원하므로 완전 동일 이슈는 아니지만 임시 비충돌 상태와 충돌 가능

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

## 8-1. 팀전 구현 시 주의사항

### 기본 원칙
- **팀전에서 부정적 효과는 반드시 아군 제외**
- **관전자 제외만으로는 부족하며, source 기준 팀 판정까지 같이 확인해야 함**
- **타게팅과 실제 적용 로직 둘 다 팀 필터를 타야 함**

### 타게팅
- 플레이어가 원점인 탐색은 다음 형태를 우선 사용하세요.

```java
LocationUtil.withValidTarget(getPlayer(), e -> !e.equals(getPlayer()))
LocationUtil.isValidTarget(getPlayer(), target)
LocationUtil.getNearbyLivingEntities(center, radius, getPlayer(), predicate)
```

- `getEntityLookingAt(...)`처럼 source가 명확한 유틸은 팀 판정까지 포함되도록 유지하세요.

### 실제 적용 로직
- 아래 항목은 **타게팅 필터를 통과했더라도** 직접 한 번 더 점검해야 합니다.
- `target.damage(...)`
- `target.setVelocity(...)`
- 강제 `teleport(...)`
- 기절, 속박, 공포, 감속 등 디버프
- 반사/전이/추적형 효과
- 바닐라 폭발 (`createExplosion`)

### 폭발/범위형 능력
- **`createExplosion(...)`은 팀원 제외가 불가능하거나 매우 제한적**이므로 팀전에서 아군 제외가 필요하면:
- 수동 범위 탐색
- 팀 판정
- 수동 피해/넉백
- 수동 파티클/사운드
  순서로 직접 처리하세요.

### UI / 정보 노출
- 팀전 전용 정보는 **같은 팀에게만 보여야 함**
- 체력, 표식, 이름표, 추적 표시 등은 적 팀에 `0`이나 빈 값이라도 보이면 안 됨
- 필요하면 scoreboard보다 `TextDisplay + showEntity/hideEntity` 같은 viewer 제어 방식 사용

### 승패 처리
- 팀전 종료 시에는 개인 승자가 아니라 **팀 승리** 기준으로 처리
- 승리팀/패배팀 메시지, 연출, 지급 보상은 팀 기준으로 분기하세요

### 팀전 보강 기준
- 폭발은 가능하면 `World#createExplosion(..., source)` 형태로 호출해서 owner 추적이 끊기지 않게 하세요.
- `TNTPrimed`를 쓰는 능력은 `setSource(player)`를 반드시 설정하세요.
- 공통 PvP 차단은 `EntityDamageByEntityEvent` 기준이므로, 폭발도 가능하면 `damager -> owner player`로 복원 가능해야 합니다.
- 팀전에서 폭발 아군 예외처리는 `데미지 차단`과 `넉백 차단`을 분리해서 생각하세요.
- 넉백까지 완전 차단이 필요하면 바닐라 폭발에 기대지 말고 수동 범위 피해/넉백으로 전환하세요.

### 직접 구현 능력 점검 우선순위
- `ProjectileHitEvent`에서 `event.getHitEntity()`에 직접 `damage`, `Stun`, `Freeze` 등을 넣는 능력
- `CustomEntity#onHitEntity(...)`에서 직접 피해를 주는 능력
- source 없는 `getNearbyLivingEntities(...)`, `getNearbyPlayers(...)`를 쓰는 능력
- 반사 피해, 전이 피해, 지연 피해, 고정 피해처럼 이벤트 공통 처리 바깥에서 실행되는 능력
- `setVelocity(...)`, `teleport(...)`로 강제 이동을 주는 능력

### 추천 구현 패턴
- 범위 탐색: `LocationUtil.getNearbyLivingEntities(center, radius, getPlayer(), predicate)`
- 플레이어 탐색: `LocationUtil.getNearbyPlayers(center, radius, getPlayer(), predicate)`
- 단일 타게팅: `LocationUtil.getEntityLookingAt(...)`
- 직접 타격 직전 최종 확인: `LocationUtil.isValidTarget(getPlayer(), target)`

### 리뷰할 때 기록할 항목
- 공통 메서드만으로 해결되는지
- 능력별 독자 예외처리가 필요한지
- 폭발 owner 추적만으로 충분한지
- 수동 폭발 처리까지 내려가야 하는지

---

## 8-2. 공용 디버프 `Slow`

### 원칙
- **복합 둔화는 포션 조합 대신 `Slow` 효과를 우선 사용**
- `Slow`는 이동, 점프, 중력, 채굴, 공격 속도, 공격 피해 감소를 **한 번에 묶어서** 적용할 수 있음
- 단일 퍼센트 슬로우가 충분하면 **프로필보다 `%` 단일 값 API를 우선 사용**

### 기본 사용법
```java
// 전 항목 50% 둔화
applySlow(target, 80, 50.0);

// 또는 effect 클래스 직접 사용
Slow.apply(target, 80, 50.0);
```

### 세부 프로필 사용법
```java
applySlow(target, 80, Slow.SlowProfile.builder()
        .movementSpeed(50.0)
        .jumpStrength(35.0)
        .gravity(40.0)          // 중력은 증가시켜 더 무겁게 만듦
        .blockBreak(70.0)
        .attackSpeed(40.0)
        .outgoingDamage(25.0)
        .build());
```

### 제공 API
- `applySlow(target, ticks)` : 기본 20% 슬로우
- `applySlow(target, ticks, percent)` : 전 항목 동일 퍼센트 슬로우
- `applySlow(target, ticks, amplifier)` : 구버전 단계형 API, 내부에서 퍼센트 프로필로 변환
- `applySlow(target, ticks, Slow.SlowProfile profile)` : 항목별 퍼센트 지정

### 영향 범위
- 이동 속도
- 비행 속도
- 웅크리기 속도
- 물속 이동
- 험지 이동
- 점프력
- 중력
- 블록 파괴 속도
- 올바른 도구 채굴 효율
- 수중 채굴 속도
- 공격 속도
- 가하는 피해

### 주의사항
- 퍼센트는 `0~95` 범위로 사용하는 것을 기본으로 생각할 것
- 중력은 감소가 아니라 **증가값**으로 적용됨. `gravity(50.0)`은 더 무거워지는 효과
- 여러 `Slow`가 겹치면 항목별로 **더 강한 퍼센트**가 유지됨
- 단순 이동 감속만 필요하면 굳이 포션 `SLOWNESS`를 섞지 말고 `Slow` 하나로 통일

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
