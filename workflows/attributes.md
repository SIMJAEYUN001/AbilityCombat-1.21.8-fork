# Attribute 보정 (사거리·속도·체력·크기·중력)

> 데미지 증감은 [damage.md](damage.md)의 `DamageModifier`로 통합되어 있습니다.
> 아래 Attribute 보정은 아직 능력별 직접 구현이 남아 있으므로, 새로 추가할 때 공통화 가능 여부를 먼저 확인하세요.

## 통합 대상
| 대상 | 현재 반복 패턴 | 권장 방향 |
|------|----------------|-----------|
| 사거리 | `ENTITY_INTERACTION_RANGE` 직접 modifier/base 변경 | `RangeModifier` 또는 Attribute 공통 유틸 |
| 이동속도 | 능력별 `MOVEMENT_SPEED` modifier | Attribute 공통 유틸 |
| 최대체력 | `MAX_HEALTH#setBaseValue` 직접 변경 | 영구/임시 체력 보정 관리자 |
| 크기 | `SCALE#setBaseValue` 직접 변경 | 크기 보정 공통 유틸 |
| 중력 | `GRAVITY#setBaseValue` 직접 변경 | Attribute 공통 유틸 |

## 작성 규칙
- 임시 보정은 가능하면 `addTransientModifier` + 고유 `NamespacedKey` + 해제 로직으로 작성합니다.
- `setBaseValue()`는 게임 기본값 초기화, NPC/더미 생성, 영구 최대체력 변화처럼 base 변경이 의도인 경우에만 사용합니다.
- 새 Attribute 보정 능력을 추가할 때는 `onDeactivate()` / 게임 종료 / 사망 복구 경로를 먼저 설계합니다.
- 같은 Attribute를 여러 능력이 건드릴 수 있으면 base 값을 직접 덮지 말고 modifier 기반으로 누적되게 구현합니다.
- 플레이어 크기(SCALE) 변경 능력은 **무적 해제 후(게임 시작 시점)** 적용합니다.
- 플레이어 크기(SCALE) 변경은 `ScaleAttributeUtil.applyBaseScalar(...)` 또는 `ScaleAttributeUtil.applyTotalScalar(...)` + `removeScaleModifier(...)`로 관리합니다.
- 크기 판정/비교는 `ScaleAttributeUtil.getScaleWithoutDash(entity)`를 사용합니다. 대시처럼 충돌 회피용으로만 들어가는 임시 크기 보정은 능력 판정에 섞지 않습니다.
- 크기 base 값을 저장해야 하는 효과는 `SCALE#getValue()`가 아니라 `ScaleAttributeUtil.getBaseScale(entity)`를 사용합니다.

> 현재 통합 유틸이 없는 대상별 능력 목록은 [refactor-candidates.md](refactor-candidates.md)를 참고하세요.
