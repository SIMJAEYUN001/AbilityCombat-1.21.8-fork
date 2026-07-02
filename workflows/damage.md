# 피해 증감 통합 규칙 (`DamageModifier`)

## 원칙
- **받는 피해/주는 피해 증감은 반드시 통합 메서드만 사용하세요.**
- 이벤트 1회성 보정: `AbilityBase.modifyDamage(...)`
- 지속형 보정: `DamageModifier.apply(...)` / `DamageModifier.remove(...)`
- `setHealth()`로 직접 체력을 깎는 방식은 화상 스택처럼 명시적으로 설계된 예외가 아니면 **금지**합니다.

## 채널
```java
DamageModifier.DamageChannel.INCOMING // 받는 피해
DamageModifier.DamageChannel.OUTGOING // 주는 피해
```

능력 클래스에서는 `AbilityBase`의 상수를 사용하세요.

```java
INCOMING_DAMAGE // 받는 피해
OUTGOING_DAMAGE // 주는 피해
```

## 이벤트 기반 일회성 보정
```java
modifyDamage(event, INCOMING_DAMAGE, -25.0, 0.0); // 받는 피해 25% 감소
modifyDamage(event, OUTGOING_DAMAGE, 20.0, 0.0);  // 주는 피해 20% 증가
modifyDamage(event, OUTGOING_DAMAGE, 0.0, 1.0);   // 최종 피해 +1
```

| 인자 | 의미 |
|------|------|
| `event` | `EntityDamageEvent` 또는 `EntityDamageByEntityEvent` |
| `channel` | `INCOMING_DAMAGE` / `OUTGOING_DAMAGE` |
| `percentDelta` | `+20.0`은 20% 증가, `-25.0`은 25% 감소 |
| `flatDelta` | 최종 피해 기준 추가/감소값 |

## 지속형 보정
```java
DamageModifier.apply(target, DamageModifier.DamageChannel.INCOMING,
        200, "source_key", 16.0); // 10초간 받는 피해 16% 증가

DamageModifier.remove(target, DamageModifier.DamageChannel.INCOMING,
        "source_key");
```

- `sourceKey`는 능력별로 고유하게 작성하세요.
- 같은 `sourceKey`는 지속시간을 갱신하고, 다른 `sourceKey`는 합산됩니다.
- 지속형 보정은 반드시 종료/해제 조건에서 `remove(...)`를 호출하세요.

## 고정 피해
```java
DamageModifier.applyFlatDamage(target, amount, source);
```

- 방어력/보호를 무시하는 고정 피해가 필요할 때만 사용하세요.
- 기존 `setHealth(target.getHealth() - amount)` 대체용입니다.
- 일반 추가 피해는 고정 피해가 아니므로 `modifyDamage(event, OUTGOING_DAMAGE, 0.0, amount)`를 사용하세요.

## 사용 금지된 구형 메서드
아래 메서드는 제거되었으며 다시 만들면 안 됩니다.

- `scaleIncomingDamage(...)`, `scaleOutgoingDamage(...)`
- `increaseIncomingDamage(...)`, `decreaseIncomingDamage(...)`
- `increaseOutgoingDamage(...)`, `decreaseOutgoingDamage(...)`
- `addIncomingDamage(...)`, `addOutgoingDamage(...)`
- `DamageModifier.applyIncoming(...)`, `DamageModifier.applyOutgoing(...)`
- `DamageModifier.removeIncoming(...)`, `DamageModifier.removeOutgoing(...)`
- `DamageModifier.addIncomingPercent(...)`, `DamageModifier.addOutgoingPercent(...)`
- `DamageModifier.addIncomingFlat(...)`, `DamageModifier.addOutgoingFlat(...)`
