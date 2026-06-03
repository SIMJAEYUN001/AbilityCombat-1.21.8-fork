# 능력별 구현 현황 및 분석 리포트

현재 `src/main/java/com/abilitycombat/ability/list/`에 위치한 54개 능력의 구현 현황입니다.

| 이름 (Name) | 파일 경로 | 핵심 기믹 | 상태 | 비고 |
| :--- | :--- | :--- | :--- | :--- |
| **Lorem** | `Lorem.java` | 근접 공격 가능, 우클릭 검기 발사 (7초 쿨) | **개선 완료** | 밸런스 조정 및 리팩토링 완료 |
| **Gladiator** | `Gladiator.java` | 1v1 결투장 생성 | **보통** | 블록 기반 아레나 생성 누락 |
| **Khazhad** | `Khazhad.java` | 얼음 창 투척 및 빙결 | **우수** | 투사체 물리 연산 개선 필요 |
| **Loki** | `Loki.java` | 배후 순간이동 및 기만 | **우수** | 홀로그램 스택 UI 누락 |
| **Emperor** | `Emperor.java` | 황제 근위대 전진 배치 | **보통** | 근위대 대열(Formation) 단순함 |
| **Hacker** | `Hacker.java` | 적 해킹 및 위치 노출 | **보통** | 프로그레스 바 UI 및 이펙트 부족 |
| **Glacier** | `Glacier.java` | 광역 빙결 및 체력 재생 | **우수** | 빙결 상태 이상 로직 단순화됨 |
| **Void** | `Void.java` | 보이드 차원문 및 순간이동 | **우수** | 무적 시간 액션바 표시 누락 |
| **PenetrationArrow** | `PenetrationArrow.java` | 블록 관통 화살 및 특수탄 | **보통** | 커스텀 투사체 엔진 미적용 |
| **SwordMaster** | `SwordMaster.java` | 다수의 부유 검 소환 및 발사 | **최상** | 프로젝트 내 가장 복잡한 로직 |
| **Nex** | `Nex.java` | 공중 도약 후 강력한 강습 | **우수** | 착지 지점 파티클 연출 화려함 |
| **Ares** | `Ares.java` | 돌진 및 적 견인 | **보통** | 지속적 인력(Pull) 연산 부족 |
| **Assassin** | `Assassin.java` | 은신 및 후방 추가 데미지 | **준수** | 단순 포션 효과 위주 |
| **TimeRewind** | `TimeRewind.java` | 과거 위치 및 상태로 회귀 | **우수** | 틱 단위 위치 추적 구현됨 |
| **SoulEncroach** | `SoulEncroach.java` | 적 영혼 침식 및 디버프 | **보통** | 이펙트 위주의 구현 |
| **Stalker** | `Stalker.java` | 특정 대상 추적 및 강화 | **보통** | 단순 거리 기반 위주 |
| **Zombie** | `Zombie.java` | 사망 시 부활 및 감염 | **보통** | 기본 속성 변경 위주 |
| **Vampire** | `Vampire.java` | 피의 힘 스택 및 광역 고정 피해 | **우수** | 스택 기반 성장형 능력으로 개편됨 |
| **Zeus** | `Zeus.java` | 번개 소환 및 감전 | **우수** | 최적화 및 스턴 로직 개선 완료 |
| **JellyFish** | `JellyFish.java` | 근접 공격 시 기절 | **우수** | 8초 쿨타임 및 액션바 최적화 적용 |
| **Morpheus** | `Morpheus.java` | 주변 플레이어 수면 | **우수** | 30초 쿨타임 및 관전 대상 제외 적용 |
| **Ira** | `Ira.java` | 3회 피격 시 폭발 | **우수** | 폭발력 2.0으로 밸런스 조정 완료 |
| **Kidnap** | `Kidnap.java` | 대상 납치 및 투척 | **우수** | 납치 중인 대상 무적 효과 삭제 완료 |

---

## 🛠 공통 개선 과제 (Roadmap)

1.  **UI/UX 고도화 (Critical)**
    - 모든 능력의 쿨타임과 스택 정보를 **Actionbar**에 통합 표시.
    - 스택형 능력(Loki, Glacier 등)은 대상 머리 위 **Hologram** 표시 도입.

2.  **커스텀 상태 이상 시스템 (High)**
    - `Stun`, `Freeze`, `Silence`, `Bleed` 등을 `AbilityBase` 수준에서 공통 관리하도록 리팩토링.
    - 현재는 각 능력 파일 내에서 개별적으로 제어 중.

3.  **VFX 엔진 강화 (Medium)**
    - 'Legacy' 버전에 존재하는 벡터 회전 연산 파티클 라이브러리 이식.
    - 마법 원(Magic Circle), 날개(Wings) 등의 복합적인 연출 추가.

4.  **투사체 물리 서버화 (Medium)**
    - `PenetrationArrow`와 `Khazhad` 등 투사체 중심 능력을 위한 독립적인 `Task` 기반 물리 엔진 적용.
    - 틱당 위치 보정을 통해 1.21.1의 투사체 감지 오류 최소화.

---

## 공통 군중제어기

상태 이상은 `com.abilitycombat.effect` 패키지의 공통 API를 우선 사용합니다.

| 상태 | 적용 API | 효과 |
|------|----------|------|
| 기절 | `Stun.apply(target, ticks)` | 이동불가 + 대상이 가하는 `EntityDamageByEntityEvent` 취소 |
| 속박 | `Bind.apply(target, ticks)` | 이동불가만 적용 |
| 무장해제 | `Disarm.apply(target, ticks)` | 대상이 가하는 `EntityDamageByEntityEvent` 취소 |
| 빙결 | `Freeze.apply(target, ticks)` | 기존 빙결 이동불가 유지 |

- 피해 차단은 `EventBridge#onEntityDamageByEntity`에서 `CrowdControl.handleDamageByEntity`로 일괄 처리합니다.
- 기절과 무장해제는 근접 공격뿐 아니라 투사체의 발사자가 상태 이상일 때도 피해 이벤트를 취소합니다.
- 이동불가는 `Stun`, `Bind`, `Freeze` 중 가장 긴 종료 시각을 기준으로 `GameManager` 이동 잠금을 유지합니다.

---

## 📝 변경 이력 (Changelog)

### 2026-06-03 군중제어기 정리

| 항목 | 변경 내용 |
|------|----------|
| **기절 (Stun)** | 기존 이동불가에 더해, 기절 중인 주체가 가하는 피해 이벤트를 취소 |
| **속박 (Bind)** | 이동불가만 적용하는 신규 효과 추가 |
| **무장해제 (Disarm)** | 피해 이벤트만 취소하는 신규 효과 추가 |
| **CrowdControl** | 상태별 이동잠금/피해차단 판정을 중앙화 |

### 2026-01-02 능력 리워크

| 능력 | 변경 내용 |
|------|----------|
| **마그넷 (Magnet)** | 투사체 흡인 → 8칸 내 플레이어 끌어당김 (8초), 폭발 시 6칸 내 20 데미지 |
| **컬스 (Curse)** | 가장 가까운 플레이어 → 바라보는 플레이어 타겟 (12칸)으로 변경 |
| **구속 (Imprison)** | 우클릭 자기보호 시 흡수1 버프(6초) 추가, **감금 시 적에게 구속1(8초) 부여** |
| **황제 (Emperor)** | **근위대 장비 변경 (금 투구, 금 칼), 버프 하향 (저항1, 힘 삭제), 지속시간 단축 (15초). 폭발 데미지 없이 밀쳐내기 거리 2배 상향.** |
| **리버스 (Reverse)** | 체력 교환 시 상대에게 흡수1 버프 10초 지급 |
| **리버레이터 (Liberator)** | **점프 없는 수평 돌진으로 변경, 발동 시 신속 2(6초) 버프 추가. 돌진 거리 3배 상향.** |
| **탄환세례 (BulletBarrage)** | **이동 주기 조정 (2틱), 탄환 속도 상향 (1.5), 유도 추적 버그 수정** |
| **벨리움 (Bellum)** | **돌진 파워(2.0) 및 지속시간(12틱) 조정 (부드러운 1틱 연산 적용)** |

### 2026-01-02 게임 기능 추가

- **킬로그 시스템**: 사망 시 킬러/피해자/능력 정보 전체 메시지 출력
- **자동 능력 정보**: 능력 선택 완료 시 자동으로 `aw info` 출력
