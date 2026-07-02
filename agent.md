# AGENT.md

AbilityCombat 능력 코드를 작성/수정하는 에이전트를 위한 진입점입니다.
이 파일을 먼저 읽고, 아래 라우팅에 따라 **해당 상황의 워크플로우 파일만** 열어 규칙을 적용하세요.

## 프로젝트 요약
- Java 21 / Paper 1.21.8 Bukkit 플러그인, 능력 기반 PvP 모드.
- 능력 구현체: `src/main/java/com/abilitycombat/ability/list/`
- 능력 등록: `abilities.yml` + `AbilityCombat#registerAbilities()`
- 상세 아키텍처/빌드는 [README.md](README.md) 참고.

## 작업 순서
1. 아래 **체크리스트**를 훑어 관련 항목을 파악합니다.
2. **라우팅 표**에서 지금 하는 작업에 맞는 워크플로우 파일을 **필요한 것만** 엽니다.
3. 규칙을 적용해 구현하고, 완료 후 체크리스트로 자기 점검합니다.
4. 능력을 추가/변경했으면 `abilities.yml`과 등록부를 함께 갱신합니다.

## 체크리스트
새 능력을 개발하거나 기존 능력을 수정할 때의 빠른 리뷰용입니다. 각 항목의 세부 규칙과 코드 예시는 링크된 워크플로우 파일에 있습니다.

- [ ] `@EventHandler` 대신 `EventBridge`, 개별 `runTaskTimer` 대신 `registerTick()` 사용 → [timers-and-events](workflows/timers-and-events.md)
- [ ] `onDeactivate()` / `onDestroy()`에서 틱·구독·리소스 정리
- [ ] 받는/주는 피해 증감은 `modifyDamage(...)` 또는 `DamageModifier.apply/remove(...)`만 사용 → [damage](workflows/damage.md)
- [ ] 고정 피해는 `DamageModifier.applyFlatDamage(...)`, 화상 스택 예외 외 `setHealth()` 직접 차감 금지
- [ ] 사거리/이동속도/최대체력/크기/중력 등 Attribute 보정은 개별 modifier 추가 전 공통화 가능 여부 확인 → [attributes](workflows/attributes.md)
- [ ] 플레이어 크기(SCALE) 변경은 무적 해제 후(게임 시작 시점) 적용
- [ ] `world.getLivingEntities()` 대신 `NearbyEntityCache`/`LocationUtil`, `spawnParticle()` 대신 `ParticleUtil`, 필요 시 `VectorPool`/`LocationPool` → [nearby-and-particles](workflows/nearby-and-particles.md)
- [ ] 능력용 ArmorStand는 `AbilityCombat.markAbilityArmorStand()`로 태그
- [ ] 액션바는 `getActionbarChannel()`, 철괴 능력은 `applyIronCooldownIfEmpty` 호출 → [ui-cooldown](workflows/ui-cooldown.md)
- [ ] 은신은 상태 저장-복원 대신 강제 on/off, 시작 전 `SprintHudService.cancelDashState(player)` 호출 → [stealth-and-glow](workflows/stealth-and-glow.md)
- [ ] viewer별 발광은 직접 scoreboard 팀 대신 `FakeGlow.show/hide` 우선
- [ ] 주변 탐색은 `withValidTarget()` / `isValidTarget()`로 관전자 제외 → [spectator-and-teams](workflows/spectator-and-teams.md)
- [ ] 팀전 영향을 받는 타게팅/적용은 source-aware `withValidTarget(getPlayer(), ...)` / `isValidTarget(getPlayer(), target)` 사용
- [ ] 직접 `damage()`/`setVelocity()`/강제 이동/디버프/폭발 피해 시 팀원 제외 확인, 바닐라 `createExplosion()`은 수동 처리 검토
- [ ] 복합 둔화는 포션 조합 대신 `Slow` 사용 → [slow-debuff](workflows/slow-debuff.md)
- [ ] `@AbilityManifest`의 `name`과 `abilities.yml`의 `name`이 정확히 일치 (영어 이름 공백 없음) → [ability-naming](workflows/ability-naming.md)
- [ ] 능력 추가/수정 시 `abilities.yml`에 변경 사항 기록

## 라우팅: "이런 작업이면 → 이 파일"
| 지금 하려는 작업에 이런 요소가 있으면 | 읽을 파일 |
|------|------|
| 매 틱 로직, 스케줄러, 이벤트 리스닝 | [workflows/timers-and-events.md](workflows/timers-and-events.md) |
| 피해를 주거나/받거나 증감, 고정 피해 | [workflows/damage.md](workflows/damage.md) |
| 사거리·이동속도·최대체력·크기·중력 변경 | [workflows/attributes.md](workflows/attributes.md) |
| 주변 엔티티 탐색, 파티클, `new Vector/Location`, 투사체 | [workflows/nearby-and-particles.md](workflows/nearby-and-particles.md) |
| BossBar·Actionbar 표시, 철괴 쿨타임 게이지 | [workflows/ui-cooldown.md](workflows/ui-cooldown.md) |
| 은신/투명화, 특정 플레이어에게만 보이는 발광 | [workflows/stealth-and-glow.md](workflows/stealth-and-glow.md) |
| 대상 탐색/타격, 관전자·아군 제외, 폭발, 팀전 | [workflows/spectator-and-teams.md](workflows/spectator-and-teams.md) |
| 이동/공격/채굴 등 복합 둔화 디버프 | [workflows/slow-debuff.md](workflows/slow-debuff.md) |
| 새 능력 등록, 능력이 추첨에 안 나옴 | [workflows/ability-naming.md](workflows/ability-naming.md) |

전체 워크플로우 인덱스: [workflows/README.md](workflows/README.md)

## 반드시 지킬 것 (요약)
- 직접 API 대신 프로젝트 유틸을 사용합니다: `registerTick`(스케줄러), `EventBridge`(이벤트), `DamageModifier`/`modifyDamage`(피해), `NearbyEntityCache`/`LocationUtil`(탐색), `ParticleUtil`(파티클), `FakeGlow`(발광), `Slow`(둔화).
- 주변 탐색·타격에는 관전자/아군 제외를 반드시 확인합니다.
- `onDeactivate()`/`onDestroy()`에서 틱·구독·임시 상태를 정리합니다.
- 특정 능력명이 나열된 정리 대상 목록([workflows/refactor-candidates.md](workflows/refactor-candidates.md))은 규칙이 아니라 스냅샷이므로, 손대기 전 실제 소스를 확인합니다.

## 유지보수 원칙
- 규칙을 고칠 때는 `workflows/`의 해당 파일을 먼저 수정하고, 위 체크리스트는 한 줄 요약만 갱신합니다.
- 특정 능력명을 나열하는 "정리 대상" 목록은 규칙이 아니므로 [workflows/refactor-candidates.md](workflows/refactor-candidates.md)에만 둡니다.
