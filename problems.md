# AbilityCombat - 현재 문제 목록

> 마지막 확인: 2026-03-13

`SprintHudService` 클래스의 구조적 결함으로 인해 프로젝트 전체가 컴파일되지 않는 상태입니다. 사용자가 직접 일부 경고 사항(Deprecated API)을 수정하여 관련 경고는 줄어들었으나, 핵심적인 빌드 차단 에러들이 여전히 존재합니다.

---

## 1. 심각한 컴파일 에러 (Critical Errors)

### 1-1. `SprintHudService.java` 클래스 정의 전면 오류
- **파일**: `SprintHudService.java`
- **심각도**: ❌ Error
- **내용**:
    - **Line 63**: `public class Service` -> 파일 이름(`SprintHudService.java`)과 클래스 이름이 일치하지 않습니다. `public final class SprintHudService`로 수정이 필요합니다.
    - **Line 138**: `private boolean requireResourcePack;` 뒤에 오타로 보이는 `privatezontalOffset;` 및 관련 문구들이 있어 메서드 헤더 선언문법 오류를 발생시키고 있습니다.
    - **Line 141**: `public SprintHudService(...)` -> 클래스 이름이 `Service`로 인식되고 있어, 생성자가 아닌 리턴 타입이 없는 일반 메서드로 취급되어 에러가 발생합니다.
    - **Line 151, 438**: 변수 선언부의 에러로 인해 `horizontalOffset` 변수를 인식하지 못하고 있습니다.

### 1-2. 타 모듈에서의 참조 오류
- **파일**: `AbilityCombat.java`, `AbilityCombatCommand.java`
- **심각도**: ❌ Error
- **내용**: `SprintHudService` 클래스가 정상적으로 정의되지 않아 `import` 및 타입 참조가 모두 불가능합니다.
    - `AbilityCombat.java` (Line 91, 108, 129, 130, 246 등 다수)
    - `AbilityCombatCommand.java` (Line 7, 175, 198)

---

## 2. 요약 테이블 (해결 필요 항목)

| # | 파일 | 줄 | 유형 | 설명 |
|---|------|-----|------|------|
| 1 | `SprintHudService.java` | 63 | **Error** | 클래스 이름 불일치 (`Service` -> `SprintHudService`) |
| 2 | `SprintHudService.java` | 138 | **Error** | 구문 오류 (필드 선언부 오타 및 깨짐) |
| 3 | `SprintHudService.java` | 141 | **Error** | 생성자 정의 오류 (클래스명 불일치 원인) |
| 4 | `SprintHudService.java` | 151, 438 | **Error** | `horizontalOffset` 인식 불가 |
| 5 | `AbilityCombat.java` | - | **Error** | `SprintHudService` 타입 참조 실패 |
| 6 | `AbilityCombatCommand.java` | - | **Error** | `SprintHudService` 타입 참조 실패 |

---

## 3. 해결된 항목 (Fixed)
- [x] `SprintHudService.java` 내 `isOnGround()` 관련 Deprecation 경고 수정 (Wrapper 메서드 도입)
- [x] `SprintHudService.java` 내 `setSwimming()` 관련 Deprecation 경고 수정 (Pose 사용)
