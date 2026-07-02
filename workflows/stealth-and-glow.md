# 은신/투명화 · viewer별 발광

## 은신 / 투명화 능력

### 원칙
- **대시, 스턴, 강제 시각 효과와 겹칠 수 있는 은신은 현재 invis/collidable 상태를 저장한 뒤 복원하지 마세요.**
- **기본은 강제 on/off** 입니다.
- 정말 중첩 상태 보존이 필요하면 `storedInvisible` 같은 단순 bool이 아니라 **상태 관리자(reference/owner 기반)** 로 관리합니다.

### 이유
- 대시, 테스트 대시, 기타 임시 효과도 `setInvisible(true)`, `setCollidable(false)`를 사용할 수 있습니다.
- 이 상태를 은신 능력이 "원래 상태"로 저장하면 종료 시 다시 복원되어 **무한 은신 / 충돌 비활성화**가 남습니다.

### 필수 규칙
- 은신 시작 전: `SprintHudService.cancelDashState(player)` 먼저 호출
- 은신 시작: `player.setInvisible(true)`, `player.setCollidable(false)` 강제 적용
- 은신 종료: `player.setInvisible(false)`, `player.setCollidable(true)` 강제 적용
- `hidePlayer` / `showPlayer` 사용 시: 다른 은신 상태(예: 헤르밋)와 충돌하지 않도록 "누가 숨겼는지"를 고려

---

## viewer별 발광 / 표식 UI

### 원칙
- 특정 플레이어에게만 보여야 하는 발광은 `FakeGlow.show(viewer, target, teamName, color)` / `FakeGlow.hide(...)`를 우선 사용합니다.
- 실제 `PotionEffectType.GLOWING`은 모든 플레이어에게 보이는 효과가 필요할 때만 사용합니다.
- 표식 유지시간이 끝나면 fake glow도 같이 제거해야 합니다.
- 팀전/2인전에서 팀원 식별용 발광은 같은 팀 viewer에게만 보내야 합니다.

### 피해야 할 직접 구현
- 능력 클래스마다 scoreboard 팀을 새로 만들고 색상/엔트리/해제를 직접 관리하는 방식
- `sendPotionEffectChange`와 팀 색상 처리를 능력마다 따로 작성하는 방식
- 표식 만료와 glow 해제가 서로 다른 타이머에 묶이는 방식

> 위험/분산 패턴이 남아 있는 능력 목록은 [refactor-candidates.md](refactor-candidates.md)를 참고하세요.
