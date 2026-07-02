# 구현 요건: 인형사(PuppetMaster) 인형 물 먹통 수정 + 공격 스윙 모션

## 대상 프로젝트
- Java 21 / Paper 1.21.8 Bukkit 플러그인 (AbilityCombat)
- 관련 파일:
  - `src/main/java/com/abilitycombat/ability/list/PuppetMaster.java` (능력 로직)
  - `src/main/java/com/abilitycombat/npc/PlayerReplica.java` (인형 = PlayerReplica, 물리/이동/패킷)
- 빌드: `mvn clean package` (Paper 1.21.8, NMS 리플렉션 사용 중)

## 프로젝트 규칙 (반드시 준수)
`workflows/` 폴더의 규칙을 따르세요. 특히:
- 스케줄러는 `registerTick()`/`onTick()` 사용, `runTaskTimer` 직접 사용 금지 (PuppetMaster는 이미 준수 중)
- 관전자/팀 제외는 `LocationUtil.isValidTarget(owner, target)` 사용 (이미 준수 중)
- 파티클은 `ParticleUtil` 사용
- **기존 물리/충돌 아키텍처를 뜯어고치지 말고 최소 변경으로 버그만 수정할 것**

---

## 문제 1: 인형이 물/액체에 닿으면 멈춰서 먹통이 됨 (최우선)

### 재현
- 인형이 물, 용암 등 액체 블록에 진입하거나 액체 위/근처로 이동하면 그 자리에 얼어붙어 이동/추적/복귀를 전혀 못 함.
- 잔디 등 통과 가능한 블록에서는 정상.

### 근본 원인 (확인됨)
`PlayerReplica.java`의 커스텀 충돌 판정이 **액체를 고체 벽처럼 취급**하기 때문:

```java
// PlayerReplica.java (약 466행)
private boolean isPassableSpace(Block block) {
    return block != null && !block.isLiquid() && (block.isPassable() || block.getType().isAir());
}
```

- `!block.isLiquid()` 조건 때문에 물/용암 칸이 "점유 불가(occupy 불가)"로 판정됨.
- 그 결과:
  1. `moveWithCollision()`에서 액체 방향으로의 모든 축 이동이 `blocked` 처리되어 velocity가 0으로 깎임 → 인형이 못 움직임.
  2. `isSupported()`(약 458행)가 발밑 물 칸을 `canOccupy == false`로 보고 "땅에 서 있다"고 오판 → 물 표면에 얼어붙음.

### 요구 동작
- **인형은 물/액체를 통과 가능한 공간으로 취급해 그 안에서도 정상적으로 이동/추적/복귀할 수 있어야 한다.** (즉 액체는 이동을 막는 벽이 아님)
- 인형이 물에 빠졌을 때 velocity가 0으로 잠기지 않고, 사용자 추적·복귀 로직(`onTick` → `chaseAndAttack`/`followOwner`/RETURN_DISTANCE 텔레포트 복귀)이 계속 동작해야 한다.

### 구현 방향 (권장, 세부는 자율)
1. **핵심 수정:** `isPassableSpace(Block)`에서 액체를 통과 가능으로 처리. 즉 `block.isLiquid()`인 칸도 점유 가능한 공간으로 간주.
   - 단, `isSupported()`가 액체를 "바닥"으로 오판하지 않도록 함께 점검. 액체는 딛고 설 수 있는 지지면이 아니어야 하며(물 위에 뜬 채 얼지 않도록), 중력으로 자연히 가라앉되 아래의 안전장치로 무한 낙하만 막으면 됨.
2. **안전장치(권장):** 인형이 물/공중에서 계속 가라앉아 사용자와 `RETURN_DISTANCE(10칸)` 이상 벌어지거나 월드 최저 높이 근처로 떨어지면, 기존 복귀 로직(`puppet.teleport(owner.getLocation())`)이 정상 작동하는지 확인. 필요하면 "액체에 일정 시간 이상 갇히면 사용자에게 복귀" 폴백을 추가해도 좋음. (단 `RETURN_DISABLE_TICKS`/투척 중 상태(`launched`)와 충돌하지 않게 주의)
3. 물속에서 인형이 자연스러워 보이도록 필요하면 `puppet.setSwimming(...)`/pose를 활용해도 되지만 **필수는 아님**. 이동 먹통 해결이 우선.

### 검증 기준
- 인형이 물웅덩이/강/용암 위를 지나 사용자를 계속 따라오고, 물속 적을 추적/타격할 수 있다.
- 물에 들어가도 velocity가 0으로 잠기지 않는다.
- 사용자가 물에서 멀어지면 인형이 복귀(teleport)한다.
- 기존 지상 이동/투척(철괴 우클릭)/재소환 동작에 회귀(regression)가 없다.

---

## 문제 2: 인형이 플레이어를 때릴 때 공격(팔 휘두르기) 모션이 없음

### 현재 동작
`PuppetMaster.chaseAndAttack()` (약 264~269행)에서 인형이 대상을 타격할 때 데미지와 사운드만 재생하고, **시각적인 팔 휘두르기(swing) 애니메이션이 없어** 인형이 가만히 선 채 데미지만 주는 것처럼 보임.

```java
if (attackCooldown <= 0) {
    target.setNoDamageTicks(0);
    target.damage(PUPPET_DAMAGE, owner);
    attackCooldown = ATTACK_INTERVAL_TICKS;
    owner.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.25f);
}
```

### 요구 동작
- 인형이 대상을 타격하는 순간(위 데미지 지점)에 **메인핸드 팔 휘두르기 애니메이션**이 모든 관전 플레이어에게 보여야 한다.

### 구현 방향
1. `PlayerReplica`에 **스윙 애니메이션 API를 추가**한다. 예: `public void swingMainHand()`.
   - 인형은 실제 Bukkit `Player`가 아니라 NMS 리플렉션으로 만든 fake player(`PlayerReplica`)이므로 `Player#swingMainHand()`가 관전자에게 방송되지 않을 수 있음.
   - 확실한 방법은 **`ClientboundAnimatePacket`**(`net.minecraft.network.protocol.game.ClientboundAnimatePacket`, action `0` = swing main arm)을 인형을 볼 수 있는 뷰어들에게 전송하는 것.
   - `PlayerReplica`의 `NmsBridge`(파일 하단 내부 클래스)에 이미 `ClientboundSetEntityDataPacket`, `ClientboundTeleportEntityPacket`, `ClientboundPlayerInfoUpdatePacket`을 리플렉션으로 생성/전송하는 패턴이 있으니, **동일한 패턴으로 `ClientboundAnimatePacket` 생성 + 전송 메서드를 추가**한다.
   - 뷰어 목록/전송은 기존 `broadcastTeleport()` / `sendMetadata` / `refreshViewers()`가 사용하는 뷰어 순회 방식을 재사용한다. (인형은 viewer별로 보이므로 `hideFrom`/`showTo`로 관리되는 뷰어 집합에만 보내면 됨)
2. `PuppetMaster.chaseAndAttack()`의 타격 시점에서 `puppet.swingMainHand()`를 호출한다.
   - (선택) 투척 후 착지 타격 등 다른 타격 지점에도 자연스러우면 적용 가능하나 필수는 아님.

### 검증 기준
- 인형이 적을 때릴 때 관전 플레이어 화면에서 인형의 팔이 휘둘러진다.
- 애니메이션이 타격(데미지) 타이밍과 동기화된다.
- 인형은 맨손(장비 없음)이므로 손 스윙만 보이면 됨.
- 패킷 리플렉션이 서버 기동/인형 소환 시 예외를 던지지 않는다(기존 NmsBridge 예외 처리 스타일과 동일하게 방어적으로 작성).

---

## 작업 산출물
- 위 두 문제를 수정하는 최소 변경 패치.
- `mvn clean package`가 성공해야 함.
- 물리/충돌 로직 대규모 리팩터링 금지, 버그 수정과 스윙 API 추가에 한정.
- 변경 요약을 커밋 메시지 또는 출력으로 남길 것.
