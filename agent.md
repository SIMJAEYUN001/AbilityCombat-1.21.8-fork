# Implementation Plan - Machine Arm (기계팔)

## Reality Anchor
- **Timestamp**: 2026-01-29
- **Mode**: Online

## Goal
- **능력 이름**: 기계팔 (MachineArm)
- **쿨타임**: 12초
- **발동 조건**: 철괴 우클릭
- **기능**: 전방으로 그랩을 발사하여 적중한 대상을 자신의 위치로 끌어옴.

## Approach Options (Implementation Probability Distribution)
사용자의 요청에 따라 그랩 구현 방식의 장단점과 추천도(성공/만족 확률)를 분석합니다.

### 1. Hybrid: Vector Logic + ArmorStand Visual (Blitzcrank Style) (추천도: 100%)
- **설명**: 이동/충돌 판정은 **Vector(수학)**로 정밀하게 처리하되, 그 좌표에 **ArmorStand(엔티티)**를 매 틱 텔레포트시켜 "날아가는 로켓 주먹"을 시각화함.
- **시각화 (Visuals)**:
  - **손 (Head)**: 투명 아머스탠드를 소환하여 머리에 `IRON_BLOCK` 또는 `IRON_TRAPDOOR`(집게 모양)를 씌움. 진행 방향으로 회전(Pose)시켜 입체감 부여.
  - **팔 (Chain)**: 플레이어와 주먹 사이를 잇는 `Particle.REDSTONE` (회색) 혹은 `Particle.CRIT`로 "늘어나는 줄" 표현.
- **장점**:
  - **완벽한 시각효과**: 블리츠크랭크 그랩처럼 거대한 물체(손)가 날아가는 느낌을 줌.
  - **성능 유지**: 아머스탠드는 `Marker: true`, `Invisible: true`로 설정하여 서버 부하 최소화.
  - **정밀함**: 실제 엔티티 이동(Velocity)이 아닌 텔레포트 방식이므로 벽 뚫기 방지나 판정이 훨씬 정확함.
- **결론**: **채택**. 현재 코드베이스에도 동일 패턴(ArmorStand 투사체)이 있어 구현/디버깅이 가장 안정적입니다. (참고: `GrapplingHook`)

### 2. Armor Stand Projectile (추천도: 60%)
- **설명**: 투명 아머스탠드를 소환하여 머리에 아이템(예: 철 블록/손)을 씌우고 날리기.
- **장점**: 그랩이 날아가는 모습이 시각적으로 뚜렷함 (블록/아이템 모델 활용).
- **단점**:
  - **성능 이슈**: 짧은 쿨타임 스킬에 엔티티를 자주 소환/삭제하면 서버 부담.
  - **히트박스**: 아머스탠드 자체 히트박스와 실제 판정이 꼬일 수 있음.

### 3. Ray-Tracing / Hitscan (추천도: 30%)
- **설명**: 발사 즉시 일직선상의 적을 감지 (즉발).
- **장점**: 구현이 매우 쉬움.
- **단점**: "손을 뻗어서 잡는다"는 느낌이 없음 (그냥 즉시 당겨짐). 피할 수 없는 공격이 되어 밸런스 붕괴 가능성.

### 4. Fishing Hook (추천도: 10%)
- **설명**: 바닐라 낚시찌 엔티티 활용.
- **장점**: 물리 엔진(중력) 자동 적용.
- **단점**: 커스텀 로직과 섞이기 힘들고, 당기는 힘을 제어하기 어려움.

---

## Selected Approach: Hybrid (Vector Logic + ArmorStand Visual)

## Acceptance Criteria
- [ ] 철괴 우클릭 시 전방으로 파티클(그랩)이 날아가야 함.
- [ ] 사거리(약 10~15칸) 내에 적(Entity)이 있으면 충돌 판정.
- [ ] 충돌 시 대상을 플레이어 위치(혹은 그랩 회수 위치)로 강하게 끌어당김(Velocity).
- [ ] 쿨타임 12초 적용.
- [ ] 벽(Block)에 닿으면 그랩이 취소되거나 막혀야 함.

## Implementation Plan (Revised)
### 0) Design Notes (코드베이스 맞춤)
- 투사체 패턴은 `GrapplingHook`처럼 **ArmorStand 1개를 매틱 텔레포트 + 근처 엔티티/블록 체크**로 시작합니다.
- 타겟 필터는 `LocationUtil.isValidTarget` + 자기 자신 제외 + ArmorStand/Mannequin 제외를 기본값으로 고정합니다.
- 시각효과는 `ParticleUtil`을 사용해 글로벌 시각효과 설정(거리/간격)을 존중합니다.
- “끌어오기”는 1회 `setVelocity`보다 **여러 틱(예: 10틱) 동안 보정**이 체감/성공률이 높습니다.
  - 중간에 블록이 끼면(레이트레이스/Line-of-sight 실패) 즉시 중단하여 벽 끼임을 최소화합니다.

### 1) State Machine (핵심)
- `IDLE` → `FIRING`(투사체 진행) → `PULLING`(타겟 당김) → `END`(정리 + 쿨타임)
- 취소 조건(공통): 플레이어 오프라인/사망, 월드 null, 최대 시간 초과, 엔티티 제거됨

### 2) Class Creation
1. `src/main/java/com/abilitycombat/ability/list/MachineArm.java` 생성
   - `AbilityBase` 상속, `ActiveHandler` 구현
   - `@AbilityManifest` 작성 (이름/설명/요약/등급/종족)
2. `AbilityCombat.registerAbilities()`에 `AbilityFactory.register(MachineArm.class)` 추가

### 3) Active Trigger
- `activeSkill(Material.IRON_INGOT, RIGHT_CLICK)`에서만 동작
- `Cooldown(12)` 체크 후 `FIRING` 시작

### 4) FIRING (Projectile)
- 발사 시점:
  - 시작 위치: `player.getEyeLocation()`
  - 방향: `eye.getDirection().normalize()`
  - ArmorStand 소환(손 모델) + `AbilityCombat.markAbilityArmorStand(stand)`
- 매 틱:
  - `next = current + direction * speed`
  - 블록 충돌: `next.getBlock().isSolid()`이면 종료(회수 없이 삭제)
  - 엔티티 충돌: `LocationUtil.getNearbyLivingEntities(next, hitRadius, ...)`로 1명만 획득
  - 체인 파티클(플레이어 ↔ 주먹) + 투사체 파티클

### 5) PULLING (끌어오기)
- 적중 순간:
  - (선택) 짧은 `Stun.apply(target, x)` 또는 `lockMovement(target, x)`로 “잡힘” 연출
  - `pullTicksRemaining = N`으로 전환
- 매 틱:
  - `toPlayer = playerLocation - targetLocation`
  - 거리 임계값(예: < 1.5칸)이면 성공 종료
  - 중간 블록 체크(간단): `player.hasLineOfSight(target)`가 false면 중단 종료
  - `target.setVelocity(toPlayer.normalize() * pullForce + yBoost)`로 지속 당김

### 6) Cleanup
- `onDeactivate/onDestroy`에서 ArmorStand 및 내부 상태(프로젝트/풀링) 정리
- 쿨타임 시작

### 7) Upgrade Path (필요 시)
- 투사체가 너무 빠르거나 가끔 “통과”하면 `CustomEntity`(rayTrace 기반)로 충돌만 교체하고, 시각화 ArmorStand는 그대로 따라가게 전환합니다.

### 8) Hunter Ability Fix (Explosion Effect Radius)
- **Goal**: 사냥꾼(Hunter)의 폭발 이펙트가 실제 판정(`EXPLOSION_RADIUS = 3.0`)보다 넓게 보이는 문제 수정.
- **Changes**: `triggerExplosion` 메서드 내 파티클 확산 범위(offset) 축소.
  - `FLAME` 파티클: 2.0 -> 1.2
  - `LAVA` 파티클: 1.5 -> 0.8
  - `SMOKE` 파티클: 1.5 -> 0.8

## Affected Files
- [UPDATE] `agent.md`
- [NEW] `src/main/java/com/abilitycombat/ability/list/MachineArm.java`
- [UPDATE] `src/main/java/com/abilitycombat/AbilityCombat.java`
- [MODIFY] `src/main/java/com/abilitycombat/ability/list/Hunter.java` (Explosion effect adjustment)

## Risks & Rollback
- **Risk**: 끌어오는 속도가 너무 빠르거나 느려서 "납치" 느낌이 안 날 수 있음.
  - **Mitigation**: `Vector`의 multiply 값을 조정하며 테스트.
- **Risk**: 대상이 벽 뒤에 있을 때 끌어오면 벽에 끼일 수 있음.
  - **Mitigation**: Pull 단계에서 line-of-sight / rayTraceBlocks 체크 후 막히면 즉시 중단.
