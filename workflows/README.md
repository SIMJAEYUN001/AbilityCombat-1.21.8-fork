# AbilityCombat 워크플로우 가이드

능력을 개발/수정할 때 **상황에 맞는 파일만** 골라 읽으세요.
빠른 리뷰는 [체크리스트](../agent.md#체크리스트)를 먼저 확인하고, 세부 규칙은 아래 표에서 해당 상황으로 이동합니다.

| 상황 | 파일 |
|------|------|
| 주기적 로직 / 이벤트 처리 | [timers-and-events.md](timers-and-events.md) |
| 받는·주는·고정 피해 증감 | [damage.md](damage.md) |
| 사거리·속도·체력·크기·중력 등 Attribute 보정 | [attributes.md](attributes.md) |
| 주변 탐색·파티클·객체풀·커스텀 엔티티 | [nearby-and-particles.md](nearby-and-particles.md) |
| BossBar·Actionbar·철괴 쿨타임 게이지 | [ui-cooldown.md](ui-cooldown.md) |
| 은신/투명화 · viewer별 발광 | [stealth-and-glow.md](stealth-and-glow.md) |
| 관전자 제외 · 팀전 안전 처리 | [spectator-and-teams.md](spectator-and-teams.md) |
| 복합 둔화(Slow) 디버프 | [slow-debuff.md](slow-debuff.md) |
| 능력 이름 규칙 (`@AbilityManifest` ↔ `abilities.yml`) | [ability-naming.md](ability-naming.md) |
| 리팩터링/통합 후보 (낡을 수 있는 스냅샷) | [refactor-candidates.md](refactor-candidates.md) |

## 사용 원칙
- 규칙과 감사 스냅샷을 섞지 마세요. 특정 능력명을 나열하는 "정리 대상" 목록은 `refactor-candidates.md`에만 둡니다.
- 규칙을 고칠 때는 세부 워크플로우 파일을 먼저 수정하고, `agent.md` 체크리스트는 한 줄 요약만 갱신합니다.
