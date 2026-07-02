# 관전자 제외 · 팀전 안전 처리

## 관전자 제외 (`isValidTarget`)
- **주변 엔티티를 탐색하거나 타겟팅할 때 반드시 관전자를 제외해야 합니다.**
- `LocationUtil.isValidTarget(LivingEntity)` 또는 `LocationUtil.withValidTarget(Predicate)`를 사용합니다.

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

## 팀전 구현 시 주의사항

### 기본 원칙
- **팀전에서 부정적 효과는 반드시 아군 제외.**
- **관전자 제외만으로는 부족**하며, source 기준 팀 판정까지 같이 확인해야 합니다.
- **타게팅과 실제 적용 로직 둘 다** 팀 필터를 타야 합니다.

### 타게팅
플레이어가 원점인 탐색은 source-aware 형태를 우선 사용합니다.

```java
LocationUtil.withValidTarget(getPlayer(), e -> !e.equals(getPlayer()))
LocationUtil.isValidTarget(getPlayer(), target)
LocationUtil.getNearbyLivingEntities(center, radius, getPlayer(), predicate)
```

- `getEntityLookingAt(...)`처럼 source가 명확한 유틸은 팀 판정까지 포함되도록 유지합니다.

### 실제 적용 로직 (타게팅 필터를 통과했더라도 직접 재점검)
- `target.damage(...)`
- `target.setVelocity(...)`
- 강제 `teleport(...)`
- 기절, 속박, 공포, 감속 등 디버프
- 반사/전이/추적형 효과
- 바닐라 폭발 (`createExplosion`)

### 폭발/범위형 능력
- **`createExplosion(...)`은 팀원 제외가 불가능하거나 매우 제한적**입니다. 팀전에서 아군 제외가 필요하면 다음 순서로 직접 처리하세요.
  1. 수동 범위 탐색
  2. 팀 판정
  3. 수동 피해/넉백
  4. 수동 파티클/사운드
- 폭발은 가능하면 `World#createExplosion(..., source)` 형태로 호출해 owner 추적이 끊기지 않게 합니다.
- `TNTPrimed`를 쓰는 능력은 `setSource(player)`를 반드시 설정합니다.
- 공통 PvP 차단은 `EntityDamageByEntityEvent` 기준이므로, 폭발도 가능하면 `damager -> owner player`로 복원 가능해야 합니다.
- 팀전에서 폭발 아군 예외는 `데미지 차단`과 `넉백 차단`을 분리해서 생각하고, 넉백까지 완전 차단이 필요하면 수동 범위 피해/넉백으로 전환합니다.

### UI / 정보 노출
- 팀전 전용 정보는 **같은 팀에게만 보여야 함.**
- 체력, 표식, 이름표, 추적 표시 등은 적 팀에 `0`이나 빈 값이라도 보이면 안 됩니다.
- 필요하면 `FakeGlow`, `TextDisplay + showEntity/hideEntity` 같은 viewer 제어 방식을 사용합니다.

### 승패 처리
- 팀전 종료 시에는 개인 승자가 아니라 **팀 승리** 기준으로 처리합니다.
- 승리팀/패배팀 메시지, 연출, 지급 보상은 팀 기준으로 분기합니다.

### 리뷰 체크 항목
- 공통 메서드만으로 해결되는지
- 능력별 독자 예외처리가 필요한지
- 폭발 owner 추적만으로 충분한지, 아니면 수동 폭발 처리까지 내려가야 하는지
