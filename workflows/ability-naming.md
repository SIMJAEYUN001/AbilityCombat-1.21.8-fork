# 능력 이름 규칙 (`@AbilityManifest` ↔ `abilities.yml`)

## 중요
- **`@AbilityManifest`의 `name` 속성과 `abilities.yml`의 `name` 필드는 글자 하나까지 완벽하게 일치해야 합니다.**
- 이름이 불일치하면 해당 능력은 **추첨 풀에서 제외**되어 게임에 등장하지 않습니다.

## 올바른 형식
```
한글이름 (EnglishName)
```

- 괄호 안의 영어 이름에는 **공백이 없어야** 합니다.
- 클래스명과 영어 이름을 동일하게 유지합니다.

```java
// ✅ 올바른 예시
@AbilityManifest(name = "거인 학살자 (GiantSlayer)", ...)  // 클래스: GiantSlayer.java

// ❌ 잘못된 예시
@AbilityManifest(name = "거인 학살자 (Giant Slayer)", ...) // 공백 포함 → 추첨 제외
@AbilityManifest(name = "설인 (Yeti)", ...)                // YAML과 불일치 → 추첨 제외
```

## 체크 방법 (새 능력 추가 시)
1. `@AbilityManifest`의 `name` 값 확인
2. `abilities.yml`에 동일한 `name` 값으로 등록
3. 영어 이름에 공백이 없는지 확인

> [!CAUTION]
> 이름 불일치는 컴파일 에러를 발생시키지 않아 발견이 어렵습니다.
> 능력이 추첨에 나오지 않으면 가장 먼저 이름 일치 여부를 확인하세요.
