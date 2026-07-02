# 공용 디버프 `Slow`

## 원칙
- **복합 둔화는 포션 조합 대신 `Slow` 효과를 우선 사용**합니다.
- `Slow`는 이동, 점프, 중력, 채굴, 공격 속도, 공격 피해 감소를 **한 번에 묶어서** 적용할 수 있습니다.
- 단일 퍼센트 슬로우가 충분하면 **프로필보다 `%` 단일 값 API를 우선 사용**합니다.

## 기본 사용법
```java
// 전 항목 50% 둔화
applySlow(target, 80, 50.0);

// 또는 effect 클래스 직접 사용
Slow.apply(target, 80, 50.0);
```

## 세부 프로필 사용법
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

## 제공 API
- `applySlow(target, ticks)` : 기본 20% 슬로우
- `applySlow(target, ticks, percent)` : 전 항목 동일 퍼센트 슬로우
- `applySlow(target, ticks, amplifier)` : 구버전 단계형 API, 내부에서 퍼센트 프로필로 변환
- `applySlow(target, ticks, Slow.SlowProfile profile)` : 항목별 퍼센트 지정

## 영향 범위
이동 속도, 비행 속도, 웅크리기 속도, 물속 이동, 험지 이동, 점프력, 중력, 블록 파괴 속도, 올바른 도구 채굴 효율, 수중 채굴 속도, 공격 속도, 가하는 피해.

## 주의사항
- 퍼센트는 `0~95` 범위 사용을 기본으로 생각하세요.
- 중력은 감소가 아니라 **증가값**으로 적용됩니다. `gravity(50.0)`은 더 무거워지는 효과입니다.
- 여러 `Slow`가 겹치면 항목별로 **더 강한 퍼센트**가 유지됩니다.
- 단순 이동 감속만 필요하면 포션 `SLOWNESS`를 섞지 말고 `Slow` 하나로 통일합니다.
