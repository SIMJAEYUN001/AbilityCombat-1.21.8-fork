# UI · 철괴 쿨타임 게이지

## BossBar & Actionbar
- **`player.spigot().sendMessage()`를 매 틱 호출하지 마세요.**
- `BossBarManager`와 `ActionbarChannel`을 사용합니다.
- 능력 클래스에서 `player.sendActionBar(...)`를 직접 반복 호출하지 말고 `getActionbarChannel().update/clear`를 사용합니다.

```java
// BossBar
getBossBarManager().update(player, "key", priority, title, progress, color, overlay);

// Actionbar
getActionbarChannel().update(player, "key", priority, message);
```

### 쿨타임 표기 기본 규칙
- **기본은 바닐라 게이지 + 철괴 우클릭 시 채팅 메시지(`notifyCooldown`)**
- **액션바 쿨타임은 사용자 요청이 있을 때만 구현**

---

## 철괴 바닐라 쿨타임 게이지
- **철괴 능력은 쿨타임 시작 직후 `applyIronCooldownIfEmpty`를 반드시 호출**합니다.
- **게이지는 표시용**이며, 실제 쿨타임 판정은 `Cooldown`으로 처리합니다.
- **게이지 충돌은 내부에서 자동으로 방지**됩니다 (다른 능력의 게이지는 덮어쓰지 않음).

```java
if (cooldown.isCooldown()) {
    notifyCooldown(cooldown);
    return false;
}

cooldown.start();
applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
```

### 적용 대상
- 철괴 우/좌클릭 등 **철괴로 발동되는 모든 능력**
