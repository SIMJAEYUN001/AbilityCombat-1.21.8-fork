# 타이머 · 이벤트 처리

## 1. 중앙 집중식 타이머 (`AbilityTickManager`)

- **`BukkitScheduler.runTaskTimer()`를 직접 사용하지 마세요.**
- `AbilityBase`의 `registerTick()` / `unregisterTick()`을 사용합니다.

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

- **`@EventHandler`를 능력 클래스에서 직접 사용하지 마세요.**
- `subscribeEvent(Event.class)`로 구독하고 `handleBridgeEvent()`에서 처리합니다.

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
```

### 지원되는 이벤트 목록
- `EntityDamageEvent`, `EntityDamageByEntityEvent`
- `EntityShootBowEvent`, `ProjectileHitEvent`
- `PlayerInteractEvent`, `PlayerInteractAtEntityEvent`
- `PlayerToggleSneakEvent`, `PlayerToggleFlightEvent`, `PlayerMoveEvent`, `PlayerDeathEvent`
- `PlayerTeleportEvent`, `PlayerItemHeldEvent`, `PlayerFishEvent`
- `BlockBreakEvent`, `BlockExplodeEvent`, `EntityExplodeEvent`
- `EntityRegainHealthEvent`, `EntityTargetLivingEntityEvent`

## 정리 시 주의
- `onDeactivate()` / `onDestroy()`에서 `unregisterTick()` 및 구독 해제 등 리소스 정리를 반드시 수행합니다.
