# 리팩터링 / 통합 후보 (스냅샷)

> [!NOTE]
> 이 문서는 **규칙이 아니라 감사(audit) 스냅샷**입니다. 특정 능력명을 나열하므로 코드가 바뀌면 낡습니다.
> 작업 전에 실제 소스를 grep으로 재확인하고, 정리한 항목은 이 목록에서 지우세요.

## Attribute 보정 직접 구현 (→ [attributes.md](attributes.md))
- 사거리 modifier 직접 관리: `Boxer`, `WraithForm`, `Gambler`
- 사거리 base 값 직접 변경: `ApexScope`, `Giant`
- 속도/스탯 modifier 중복 패턴: `Gambler`, `TapDancer`, `Slow`
- 크기/중력 base 값 직접 변경: `Giant`, `GiantSlayer`, `DecayRay`, `GravityField`

우선순위: 사거리 증가/감소는 아직 통합 메서드가 없으므로 리팩터링 1순위.

## 은신/투명화 상태 저장-복원 위험 (→ [stealth-and-glow.md](stealth-and-glow.md))
- 위험 패턴 후보: `Liberator`, `Soul`, `SoulEncroach`, `Scarecrow`, `Stalker`, `Hermit`, `Ghost`
- 부분 위험: `Bellum` (collidable만 저장/복원 — 완전 동일 이슈는 아니나 임시 비충돌 상태와 충돌 가능)

## viewer별 발광 분산 (→ [stealth-and-glow.md](stealth-and-glow.md))
- 자체 scoreboard 팀으로 사용자 전용 발광 관리: `FirstAidKit`
- `FakeGlow` 기반이나 유지시간/해제 패턴이 능력마다 분산: `Luna`, `Solar`, `AkashicRecord`, 팀 선택 발광

## 기타
- 일부 능력은 액션바를 직접 갱신 → `ActionbarChannel`로 점진 정리 필요 (→ [ui-cooldown.md](ui-cooldown.md))
