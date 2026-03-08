# AbilityCombat - 현재 문제 목록

> 마지막 확인: 2026-03-09

총 **9개** 경고 (warning) — 컴파일 에러는 없음

---

## 1. Deprecated API 사용

### 1-1. `JavaPlugin.getDescription()` deprecated
- **파일**: `AbilityRegistry.java` (Line 54)
- **심각도**: ⚠️ Warning
- **코드**: `plugin.getDescription().getVersion()`
- **원인**: Bukkit API에서 `JavaPlugin.getDescription()`이 deprecated됨
- **해결 방안**: `plugin.getPluginMeta().getVersion()`으로 교체

### 1-2. `ItemStack.setType(Material)` deprecated
- **파일**: `MakeshiftAnvil.java` (Line 114)
- **심각도**: ⚠️ Warning
- **코드**: `upgraded.setType(selected.upgradedType())`
- **원인**: Bukkit API에서 `ItemStack.setType()`이 deprecated됨
- **해결 방안**: `ItemStack`을 새로 생성한 뒤 메타/인챈트를 복사하는 방식으로 변경
  ```java
  // 기존
  ItemStack upgraded = selected.item().clone();
  upgraded.setType(selected.upgradedType());

  // 변경 제안
  ItemStack upgraded = new ItemStack(selected.upgradedType());
  upgraded.setItemMeta(selected.item().getItemMeta());
  // (인챈트 등 추가 복사 로직 필요)
  ```

---

## 2. 미사용 import

### 2-1. `org.bukkit.WorldBorder` 미사용
- **파일**: `Bellum.java` (Line 20)
- **심각도**: ⚠️ Warning
- **코드**: `import org.bukkit.WorldBorder;`
- **원인**: `WorldBorder`를 직접 사용하지 않고 `getWorldBorder()` 반환값을 인라인으로 사용 중
- **해결 방안**: 해당 import 줄 삭제

---

## 3. Null type safety (Unboxing 경고)

### 3-1. `Liberator.java` — `Double::sum`
- **파일**: `Liberator.java` (Line 95)
- **심각도**: ⚠️ Warning (x2)
- **코드**: `dealtDamage.merge(target.getUniqueId(), event.getFinalDamage(), Double::sum);`
- **원인**: `BiFunction<Double, Double, Double>`의 매개변수가 `double` 원시 타입으로 unchecked conversion됨 (`Double::sum`의 시그니처가 `(double, double) -> double`이기 때문)
- **해결 방안**: 명시적 람다로 교체
  ```java
  dealtDamage.merge(target.getUniqueId(), event.getFinalDamage(), (a, b) -> a + b);
  ```

### 3-2. `Themis.java` — `Integer::sum`
- **파일**: `Themis.java` (Line 55)
- **심각도**: ⚠️ Warning (x2)
- **코드**: `killCounts.merge(killer.getUniqueId(), 1, Integer::sum);`
- **원인**: `BiFunction<Integer, Integer, Integer>`의 매개변수가 `int` 원시 타입으로 unchecked conversion됨 (`Integer::sum`의 시그니처가 `(int, int) -> int`이기 때문)
- **해결 방안**: 명시적 람다로 교체
  ```java
  killCounts.merge(killer.getUniqueId(), 1, (a, b) -> a + b);
  ```

---

## 4. Potential Null Pointer Access

### 4-1. `SweepPacketSuppressor.java` — `args` may be null
- **파일**: `SweepPacketSuppressor.java` (Line 377)
- **심각도**: ⚠️ Warning
- **코드**:
  ```java
  Object arg = args[i];  // args가 null일 수 있음
  ```
- **원인**: `findCompatibleMethod`에서 `args`가 null일 때 `argCount`는 0이 되어 for문에 진입하지 않지만, 정적 분석기가 null 가능성을 경고
- **해결 방안**: null guard 추가 또는 for문 앞에 `if (args == null) return null;` 추가
  ```java
  private Method findCompatibleMethod(Class<?> type, String methodName, Object[] args) {
      if (args == null) {
          args = new Object[0];
      }
      // ...
  }
  ```

### 4-2. `ConfigGui.java` — `section` may be null
- **파일**: `ConfigGui.java` (Line 168)
- **심각도**: ⚠️ Warning
- **코드**:
  ```java
  String world = section != null ? section.getString("world", "").trim() : "";
  boolean configured = world != null && !world.isEmpty();
  String pos = configured
      ? "§f" + world + " §7(" + section.getDouble("x", 0) + ...  // section이 null일 수 있음
  ```
- **원인**: `configured`가 true이면 `section`도 non-null이 보장되지만, 정적 분석기는 `section`의 null 가능성을 추적함
- **해결 방안**: `configured` 조건에 `section != null`을 명시적으로 추가
  ```java
  String pos = (configured && section != null)
      ? "§f" + world + " §7(" + section.getDouble("x", 0) + ", "
              + section.getDouble("y", 0) + ", " + section.getDouble("z", 0) + ")"
      : "§c미설정";
  ```

---

## 요약 테이블

| # | 파일 | 줄 | 유형 | 설명 |
|---|------|-----|------|------|
| 1 | `AbilityRegistry.java` | 54 | Deprecated API | `getDescription()` → `getPluginMeta()` |
| 2 | `MakeshiftAnvil.java` | 114 | Deprecated API | `setType()` → 새 ItemStack 생성 |
| 3 | `Bellum.java` | 20 | 미사용 import | `WorldBorder` import 삭제 |
| 4 | `Liberator.java` | 95 | Null safety | `Double::sum` → `(a, b) -> a + b` |
| 5 | `Themis.java` | 55 | Null safety | `Integer::sum` → `(a, b) -> a + b` |
| 6 | `SweepPacketSuppressor.java` | 377 | Null pointer | `args` null guard 추가 |
| 7 | `ConfigGui.java` | 168 | Null pointer | `section` null 체크 보강 |
