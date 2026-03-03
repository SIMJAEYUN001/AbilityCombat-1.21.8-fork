# AbilityCombat

`AbilityCombat`은 **Paper/Spigot 기반 마인크래프트 전투 플러그인**으로, 능력 기반 PvP 게임 모드를 제공합니다.

## 프로젝트 개요

- Java 21 기반 플러그인
- Paper API 1.21.x 대상
- 게임 흐름 구현
  - 게임 시작/종료 관리
  - 능력 추첨 및 재추첨
  - 무적 시간, 본게임 시간 카운트다운
  - 단계별 월드 보더 축소
  - 전투/사망/관전자/점수판/시각효과 처리

## 구성 요소

- `com.abilitycombat` 패키지 기반 메인 플러그인 엔트리 (`AbilityCombat`)
- `ability`: 능력 정의, 랭크/아이콘/설명, 능력 클래스 등록
- `game`: 게임 상태/참가자/맵/라운드/이벤트 처리
- `gui`: 디버그, 능력 목록, 설정, 툴킷 UI
- `event`: 이벤트 브릿지
- `entity`, `effect`, `utils`, `vfx`: 커스텀 엔티티, 이펙트, 유틸, 파티클 기능

## 특징

- 50개가 넘는 능력 클래스 등록(능력 진입점은 `AbilityCombat#registerAbilities()`)
- 능력 목록은 `abilities.yml`에서 동적으로 로드
- 맵 목록/설정은 `MapManager`와 `maps.yml`로 관리
- 설정은 `config.yml` 값으로 동적으로 동작 조정

## 사전 요구사항

- JDK 21
- Paper 서버 1.21+
- Maven

## 빌드

```bash
mvn clean package
```

- 출력 파일은 `target/` 경로의 jar

## 설치

1. Maven으로 빌드
2. `target/`에서 생성된 jar를 서버의 `plugins/` 폴더에 복사
3. 서버 재시작 또는 플러그인 리로드
4. 관리자 권한으로 `/aw start` 실행

## 명령어

- `/aw info`
  - 내 능력 정보 표시
- `/aw abilities` / `/aw ability`
  - 능력 목록 표시(플레이어는 조회 GUI 오픈)
- `/aw start`
  - 게임 시작 (관리자)
- `/aw stop`
  - 게임 종료 (관리자)
- `/aw debug`
  - 능력 디버그 GUI 열기 (관리자)
- `/aw toolkit`
  - 기본 지급템 설정 GUI (관리자)
- `/aw config`
  - 게임 설정/맵 설정 GUI (관리자)
- `/aw config reload`
  - 설정/능력 데이터 새로 불러오기 (관리자)
- `/aw config setspawn`
  - 시작 지점 설정 (관리자)
- `/aw test <횟수>`
  - 능력 추첨 테스트 (관리자)

필요 권한: `abilitycombat.admin` (또는 OP)

> 참고: `plugin.yml`의 `/aw` 기본 설명 외에도, 실제 서브커맨드는 `AbilityCombatCommand`에서 처리됩니다.

## 기본 설정 (`config.yml`)

주요 값 예시:

```yaml
game:
  invincibility-seconds: 70
  duration-seconds: 720
  allow-debug-during-game: false

ability:
  selection-seconds: 50
  reroll-count: 1

world-border:
  initial-radius: 200
  shrink-seconds: 3
  phases:
    - time: 60
      radius: 200
    - time: 60
      radius: 150
    - time: 60
      radius: 100
    - time: 60
      radius: 20
    - time: 60
      radius: 3

spectator:
  hide-from-alive: true
map-restore:
  enabled: true
mob-spawn:
  block-natural: true
crafting:
  enabled: true
```

## 능력 추가 가이드

1. `src/main/java/com/abilitycombat/ability/list/`에 능력 클래스 생성
2. `src/main/resources/abilities.yml`에 항목 추가
   - `name`, `rank`, `icon`, `summary`
3. `AbilityCombat#registerAbilities()`에 클래스 등록
4. 능력에 필요한 설정 키를 `config.yml`/기본 설정 파일에 추가

## Credits

작성자: `Antigravity`
