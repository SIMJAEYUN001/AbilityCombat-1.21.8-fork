package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityDescriptor;
import com.abilitycombat.ability.AbilityFactory;
import com.abilitycombat.ability.AbilityManifest;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.abilitycombat.ability.list.InspiredAbilitySpec.CrowdControlType.BIND;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.CrowdControlType.DISARM;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.CrowdControlType.FREEZE;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.CrowdControlType.NONE;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.CrowdControlType.STUN;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.ALLY;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.ASSASSIN;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.BLACK_HOLE;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.BLAST;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.CURSE;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.DASH;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.DEFLECT;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.EXECUTE;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.FROST;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.GAMBLE;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.GLASS_CANNON;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.GUARD;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.MARK;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.NOVA;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.PORTAL;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.PULL;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.SINGLE;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.SOUL;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.SUMMON;
import static com.abilitycombat.ability.list.InspiredAbilitySpec.Style.SWAP;

public final class InspiredAbilityPack {

    private static final List<InspiredAbilitySpec> SPECS = List.of(
            p("죽음의 손아귀", Material.WITHER_SKELETON_SKULL, PULL, BIND, 34,
                    "바라본 방향의 적들을 끌어당깁니다.", "끌려온 적에게 피해와 속박을 줍니다."),
            p("관통 스나이퍼", Material.SPYGLASS, SINGLE, DISARM, 38,
                    "바라본 적을 저격해 강한 피해를 줍니다.", "적중 대상은 잠시 무장해제됩니다."),
            p("팬데믹", Material.FERMENTED_SPIDER_EYE, SUMMON, BIND, 42,
                    "바라본 지점에 전염 구역을 펼칩니다.", "구역의 적은 감염과 속박을 받습니다."),
            p("쇼 타임", Material.FIREWORK_ROCKET, ALLY, NONE, 36,
                    "자신과 주변 팀원에게 신속과 재생을 줍니다.", "가까운 적은 짧게 밀려납니다."),
            p("축복", Material.GOLDEN_APPLE, ALLY, NONE, 40,
                    "주변 팀원을 회복시키고 저항을 부여합니다.", "자신도 같은 효과를 받습니다."),
            p("유성", Material.FIRE_CHARGE, BLAST, STUN, 44,
                    "바라본 지점에 유성 충격을 떨어뜨립니다.", "범위의 적에게 피해와 기절을 줍니다."),
            p("검무", Material.IRON_SWORD, ASSASSIN, NONE, 36,
                    "바라본 적 뒤로 파고들어 연속으로 베어냅니다.", "적중 대상에게 출혈을 남깁니다."),
            p("정신나갈거같아", Material.POISONOUS_POTATO, ASSASSIN, STUN, 32,
                    "바라본 적 뒤로 파고들어 베어냅니다.", "적중 대상에게 출혈과 기절을 줍니다."),
            p("흑기사", Material.NETHERITE_CHESTPLATE, GUARD, DISARM, 46,
                    "군중제어를 풀고 검은 방패를 두릅니다.", "근처 적은 피해와 무장해제를 받습니다."),
            p("로켓런쳐", Material.FIREWORK_STAR, BLAST, DISARM, 40,
                    "바라본 지점에 폭발을 일으킵니다.", "맞은 적의 공격을 잠시 끊습니다."),
            p("원한", Material.WITHER_ROSE, CURSE, NONE, 30,
                    "바라본 적에게 원한을 새깁니다.", "피해와 출혈을 주고 자신을 회복합니다."),
            p("귀차니즘", Material.HONEY_BLOCK, PULL, BIND, 28,
                    "주변 적을 느릿하게 끌어당깁니다.", "끌려온 적은 속박됩니다."),
            p("히히 못 가", Material.COBWEB, PULL, BIND, 32,
                    "주변 적을 붙잡아 자신 쪽으로 당깁니다.", "이동을 막고 약한 피해를 줍니다."),
            p("절대 영도", Material.BLUE_ICE, FROST, FREEZE, 48,
                    "바라본 지점에 빙결 폭풍을 만듭니다.", "이미 빙결된 적을 맞히면 회복합니다."),
            p("결박", Material.CHAIN, SINGLE, BIND, 24,
                    "바라본 적을 사슬로 묶습니다.", "피해와 속박을 동시에 줍니다."),
            p("투포환", Material.IRON_BLOCK, SINGLE, STUN, 26,
                    "무거운 한 방을 날려 적을 때립니다.", "적중 대상은 짧게 기절합니다."),
            p("염인", Material.BLAZE_POWDER, NOVA, STUN, 36,
                    "주변을 베어내며 불꽃 충격을 냅니다.", "가까운 적에게 피해와 기절을 줍니다."),
            p("사건의 지평선", Material.ENDER_EYE, BLACK_HOLE, BIND, 54,
                    "바라본 지점에 강한 중력장을 엽니다.", "적을 끌어당기고 실명과 속박을 줍니다."),
            p("암흑 암살자", Material.BLACK_DYE, ASSASSIN, DISARM, 34,
                    "적 뒤로 순간이동해 어둠을 남깁니다.", "출혈과 무장해제를 부여합니다."),
            p("진검승부", Material.NETHERITE_SWORD, MARK, NONE, 30,
                    "바라본 적을 결투 대상으로 표시합니다.", "표식 대상은 빛나고 약화됩니다."),
            p("다이스 갓", Material.QUARTZ, GAMBLE, NONE, 24,
                    "무작위 전투 효과를 굴립니다.", "강화, 폭발, 견인, 회복 중 하나가 발동합니다."),
            p("빠른 회복", Material.GLISTERING_MELON_SLICE, GUARD, NONE, 28,
                    "군중제어를 풀고 체력을 회복합니다.", "짧은 흡수와 저항을 얻습니다."),
            p("깃털", Material.FEATHER, PORTAL, NONE, 24,
                    "바라보는 방향으로 가볍게 이동합니다.", "대상이 있으면 뒤로 파고듭니다."),
            p("플렉터", Material.SHIELD, DEFLECT, DISARM, 34,
                    "방어막을 펼쳐 주변 적을 튕겨냅니다.", "맞은 적은 잠시 무장해제됩니다."),
            p("페르다", Material.OAK_SAPLING, ALLY, BIND, 40,
                    "주변 팀원을 회복시키고 뿌리를 뻗습니다.", "가까운 적은 속박됩니다."),
            p("에너지 블로커", Material.AMETHYST_SHARD, GUARD, NONE, 32,
                    "피해를 막는 에너지장을 두릅니다.", "군중제어를 풀고 흡수를 얻습니다."),
            p("동기화", Material.HEART_OF_THE_SEA, ALLY, NONE, 42,
                    "주변 팀원과 전투 리듬을 맞춥니다.", "신속, 재생, 저항을 함께 받습니다."),
            p("뮤즈", Material.NOTE_BLOCK, ALLY, NONE, 36,
                    "주변 팀원에게 짧은 전투 버프를 줍니다.", "가까운 적은 약하게 밀려납니다."),
            p("루나", Material.END_ROD, MARK, BIND, 34,
                    "달빛 표식을 남겨 적을 드러냅니다.", "표식 대상은 약화와 속박을 받습니다."),
            p("솔라", Material.SUNFLOWER, BLAST, STUN, 38,
                    "태양빛을 터뜨려 범위를 타격합니다.", "중심의 적은 짧게 기절합니다."),
            p("포커", Material.PAPER, GAMBLE, NONE, 24,
                    "패를 뽑아 무작위 효과를 발동합니다.", "운이 좋으면 공격과 회복이 함께 나옵니다."),
            p("권투선수", Material.IRON_AXE, SINGLE, STUN, 22,
                    "바라본 적에게 묵직한 펀치를 날립니다.", "적중 대상은 짧게 기절합니다."),
            p("룬", Material.ENCHANTED_BOOK, MARK, FREEZE, 36,
                    "룬 표식을 새겨 적을 묶습니다.", "표식 대상은 빙결되며 드러납니다."),
            p("데이터마이닝", Material.COMPARATOR, MARK, NONE, 34,
                    "바라본 적의 약점을 읽어냅니다.", "대상에게 약화와 발광을 부여합니다."),
            p("변장술", Material.PLAYER_HEAD, PORTAL, DISARM, 30,
                    "순간이동으로 위치를 흐립니다.", "대상이 있으면 뒤로 이동해 무장해제합니다."),
            p("엑시즈", Material.ENDER_PEARL, BLAST, BIND, 42,
                    "바라본 지점에 차원 충격을 냅니다.", "범위의 적을 속박합니다."),
            p("팬텀 시프", Material.PHANTOM_MEMBRANE, ASSASSIN, DISARM, 34,
                    "바라본 적 뒤로 숨어들어 훔쳐칩니다.", "출혈과 무장해제를 남깁니다."),
            p("블럭", Material.BRICKS, GUARD, BIND, 36,
                    "자신을 막아서는 장벽을 세웁니다.", "가까운 적은 피해와 속박을 받습니다."),
            p("불신", Material.BLACKSTONE, CURSE, DISARM, 32,
                    "바라본 적에게 불신을 걸어 흔듭니다.", "출혈과 무장해제를 부여합니다."),
            p("카지노", Material.GOLD_INGOT, GAMBLE, NONE, 24,
                    "판돈을 걸고 무작위 효과를 얻습니다.", "강화 또는 범위 타격이 발동합니다."),
            p("겜블러", Material.EMERALD, GAMBLE, NONE, 24,
                    "운에 맡겨 전투 효과를 굴립니다.", "방어, 공격, 견인 중 하나가 발동합니다."),
            p("레이센", Material.RABBIT_FOOT, MARK, STUN, 34,
                    "바라본 적에게 혼란 표식을 남깁니다.", "표식 대상은 드러나고 기절합니다."),
            p("블랙 패더", Material.FEATHER, PORTAL, BIND, 30,
                    "검은 깃털을 타고 빠르게 이동합니다.", "대상이 있으면 뒤를 잡고 속박합니다."),
            p("모닝스타", Material.MACE, BLAST, STUN, 44,
                    "바라본 지점에 별빛 철퇴를 내리칩니다.", "범위 적에게 피해와 기절을 줍니다."),
            p("수호천사", Material.TOTEM_OF_UNDYING, GUARD, NONE, 46,
                    "보호막을 두르고 전투를 다시 버팁니다.", "군중제어를 풀고 회복합니다."),
            p("인내심", Material.TURTLE_HELMET, GUARD, NONE, 30,
                    "군중제어를 풀고 버티는 태세를 취합니다.", "흡수와 저항을 얻습니다."),
            p("복수", Material.REDSTONE, EXECUTE, STUN, 34,
                    "약해진 적을 향해 복수의 일격을 날립니다.", "체력이 낮은 대상에게 더 강합니다."),
            p("가시", Material.CACTUS, DEFLECT, DISARM, 30,
                    "가시 방어막으로 적을 튕겨냅니다.", "가까운 적은 무장해제됩니다."),
            p("에너지 테이커", Material.ECHO_SHARD, SOUL, BIND, 36,
                    "바라본 적의 힘을 빼앗습니다.", "피해를 주고 자신을 회복합니다."),
            p("신의 가호", Material.NETHER_STAR, ALLY, NONE, 44,
                    "자신과 팀원에게 보호를 내립니다.", "재생과 저항을 부여합니다."),
            p("나 홀로 외길", Material.DIAMOND_BOOTS, GUARD, NONE, 30,
                    "혼자 버티는 태세로 몸을 굳힙니다.", "군중제어를 풀고 방어 효과를 얻습니다."),
            p("봉인자", Material.CHAIN, SINGLE, DISARM, 34,
                    "바라본 적의 공격을 봉인합니다.", "피해와 무장해제를 줍니다."),
            p("인챈트 애로우", Material.SPECTRAL_ARROW, SINGLE, FREEZE, 36,
                    "마력 화살로 바라본 적을 꿰뚫습니다.", "적중 대상은 빙결됩니다."),
            p("너만 때린다", Material.TARGET, MARK, BIND, 32,
                    "바라본 적을 집중 대상으로 지정합니다.", "대상은 드러나고 속박됩니다."),
            p("장미의 유혹", Material.ROSE_BUSH, PULL, BIND, 36,
                    "주변 적을 장미 향으로 끌어옵니다.", "끌려온 적은 속박됩니다."),
            p("루시페륨", Material.NETHER_WART, CURSE, STUN, 38,
                    "바라본 적에게 위험한 저주를 주입합니다.", "출혈과 기절을 남깁니다."),
            p("일격필살", Material.NETHERITE_AXE, EXECUTE, NONE, 40,
                    "바라본 적에게 단호한 일격을 넣습니다.", "체력이 낮은 적에게 훨씬 강합니다."),
            p("테슬라", Material.LIGHTNING_ROD, NOVA, STUN, 34,
                    "전기를 방출해 주변 적을 때립니다.", "맞은 적은 짧게 기절합니다."),
            p("불사조", Material.BLAZE_ROD, GUARD, NONE, 46,
                    "불꽃으로 몸을 감싸 회복합니다.", "흡수와 저항을 얻습니다."),
            p("러시안 룰렛", Material.CROSSBOW, GAMBLE, NONE, 28,
                    "무작위 전투 효과를 강하게 굴립니다.", "공격 또는 방어 효과가 발동합니다."),
            p("왕", Material.GOLDEN_HELMET, ALLY, DISARM, 42,
                    "왕의 명령으로 주변을 장악합니다.", "팀원은 강화되고 적은 무장해제됩니다."),
            p("유리 대포", Material.GLASS, GLASS_CANNON, NONE, 0,
                    "주는 피해가 25% 증가합니다.", "받는 피해가 15% 증가합니다."),
            p("순간 가속", Material.SUGAR, DASH, STUN, 26,
                    "바라보는 방향으로 빠르게 파고듭니다.", "충돌 지점의 적은 기절합니다."),
            p("주인공", Material.NAME_TAG, GUARD, NONE, 40,
                    "짧게 버티며 전투 흐름을 되찾습니다.", "회복, 흡수, 저항을 얻습니다."),
            p("명사수", Material.BOW, SINGLE, DISARM, 30,
                    "바라본 적을 정확히 쏩니다.", "적중 대상은 잠시 공격이 끊깁니다."),
            p("수녀", Material.WHITE_BANNER, ALLY, NONE, 42,
                    "주변 팀원을 치유하고 보호합니다.", "자신도 재생과 저항을 받습니다."),
            p("집행관", Material.IRON_SWORD, EXECUTE, DISARM, 36,
                    "약해진 적에게 집행의 일격을 가합니다.", "대상은 무장해제됩니다."),
            p("메아리", Material.SCULK_SENSOR, DEFLECT, STUN, 34,
                    "받아치는 파동으로 주변 적을 밀어냅니다.", "맞은 적은 짧게 기절합니다."),
            p("서큐버스", Material.PINK_DYE, SOUL, BIND, 36,
                    "바라본 적의 생명력을 빨아들입니다.", "피해를 주고 자신을 회복합니다."),
            p("파이로매니악", Material.FLINT_AND_STEEL, NOVA, STUN, 38,
                    "주변에 화염 충격을 터뜨립니다.", "가까운 적에게 피해와 기절을 줍니다."),
            p("선견지명", Material.COMPASS, MARK, NONE, 30,
                    "바라본 적의 움직임을 읽습니다.", "대상은 드러나고 약화됩니다."),
            p("피학증", Material.CHAINMAIL_CHESTPLATE, DEFLECT, NONE, 28,
                    "맞받아칠 준비를 하며 방어합니다.", "주변 적을 밀치고 저항을 얻습니다."),
            p("앨리스", Material.POPPED_CHORUS_FRUIT, SWAP, BIND, 34,
                    "바라본 적과 위치를 기묘하게 바꿉니다.", "교환된 적은 혼란과 속박을 받습니다."),
            p("호루스", Material.GOLDEN_HORSE_ARMOR, BLACK_HOLE, STUN, 48,
                    "태양의 시선으로 적을 끌어모읍니다.", "범위 적에게 실명과 기절을 줍니다."),
            p("우유부단", Material.MILK_BUCKET, GAMBLE, NONE, 24,
                    "망설임 끝에 무작위 효과를 냅니다.", "공격, 방어, 이동 중 하나가 발동합니다."),
            p("절대반지", Material.GOLD_NUGGET, GUARD, DISARM, 44,
                    "짧게 보호막을 두르고 존재감을 숨깁니다.", "가까운 적은 무장해제됩니다."),
            p("사일런트", Material.SCULK_SHRIEKER, SINGLE, DISARM, 34,
                    "바라본 적의 공격 흐름을 끊습니다.", "피해와 무장해제를 줍니다."),
            p("시간 정지", Material.CLOCK, FROST, FREEZE, 54,
                    "바라본 지점의 시간을 얼립니다.", "범위 적을 빙결합니다."),
            p("아카식 레코드", Material.WRITABLE_BOOK, MARK, NONE, 34,
                    "바라본 적의 정보를 기록합니다.", "대상은 드러나고 약화됩니다."),
            p("인페르노", Material.MAGMA_CREAM, BLAST, STUN, 42,
                    "바라본 지점에 지옥불을 터뜨립니다.", "범위 적에게 피해와 기절을 줍니다."),
            p("감시의 장막", Material.GRAY_BANNER, GUARD, NONE, 32,
                    "보호막을 펼쳐 순간적인 압박을 막습니다.", "군중제어를 풀고 흡수를 얻습니다."),
            p("강타자", Material.IRON_AXE, SINGLE, STUN, 24,
                    "바라본 적에게 강한 강타를 넣습니다.", "대상을 짧게 기절시킵니다."),
            p("고문자", Material.WITHER_ROSE, CURSE, BIND, 34,
                    "적에게 고통의 저주를 남깁니다.", "출혈과 속박을 부여합니다."),
            p("궁극의 저지 불가", Material.NETHERITE_BOOTS, GUARD, NONE, 42,
                    "군중제어를 풀고 밀고 나갑니다.", "저항과 흡수를 얻습니다."),
            p("그림자 질주", Material.ENDER_PEARL, DASH, NONE, 24,
                    "바라보는 방향으로 빠르게 질주합니다.", "도착 지점의 적에게 피해를 줍니다."),
            p("대지의 영혼", Material.GRASS_BLOCK, GUARD, BIND, 38,
                    "대지의 힘으로 버티며 뿌리를 내립니다.", "가까운 적은 속박됩니다."),
            p("바다의 영혼", Material.PRISMARINE_SHARD, ALLY, NONE, 38,
                    "주변 팀원을 부드럽게 회복합니다.", "신속과 재생을 함께 줍니다."),
            p("반발", Material.SHIELD, DEFLECT, STUN, 30,
                    "밀어내는 보호막으로 주변을 튕깁니다.", "맞은 적은 짧게 기절합니다."),
            p("선혈포식", Material.REDSTONE, SOUL, NONE, 32,
                    "바라본 적의 피를 빨아들입니다.", "피해를 주고 자신을 회복합니다."),
            p("자폭", Material.TNT, NOVA, STUN, 40,
                    "자신 주변에 큰 충격을 터뜨립니다.", "가까운 적에게 피해와 기절을 줍니다."),
            p("점멸탄", Material.SEA_LANTERN, BLAST, DISARM, 34,
                    "바라본 지점에 섬광을 터뜨립니다.", "범위 적의 공격을 잠시 끊습니다."),
            p("서리 망령", Material.PACKED_ICE, FROST, FREEZE, 42,
                    "차가운 망령을 보내 범위를 얼립니다.", "빙결 대상 타격 시 회복합니다."),
            p("처형자", Material.NETHERITE_SWORD, EXECUTE, DISARM, 36,
                    "약해진 적을 처형하듯 베어냅니다.", "체력이 낮을수록 더 아픕니다."),
            p("청부 살인마", Material.CROSSBOW, ASSASSIN, STUN, 34,
                    "바라본 적 뒤로 파고들어 타격합니다.", "출혈과 기절을 남깁니다."),
            p("취약", Material.CRACKED_DEEPSLATE_BRICKS, MARK, BIND, 30,
                    "바라본 적의 약점을 드러냅니다.", "대상은 약화와 속박을 받습니다."),
            p("천상의 신체", Material.NETHER_STAR, GUARD, NONE, 44,
                    "몸을 단단하게 만들어 버팁니다.", "회복, 흡수, 저항을 얻습니다."),
            p("최첨단 발명가", Material.REDSTONE_TORCH, ALLY, DISARM, 38,
                    "주변 팀원을 강화 장치로 보조합니다.", "근처 적의 공격을 잠시 끊습니다."),
            p("악마의 춤", Material.GOLDEN_BOOTS, DASH, BIND, 30,
                    "춤추듯 전방으로 파고듭니다.", "도착 지점의 적을 속박합니다."),
            p("천천히, 꾸준히", Material.SLIME_BALL, SINGLE, BIND, 26,
                    "바라본 적을 느리게 압박합니다.", "피해와 속박을 부여합니다."),
            p("확률적 방어", Material.IRON_DOOR, DEFLECT, NONE, 30,
                    "방어 태세로 주변을 밀쳐냅니다.", "저항과 흡수를 얻습니다."));

    static {
        validate();
    }

    private InspiredAbilityPack() {
    }

    public static void registerAll() {
        for (InspiredAbilitySpec spec : SPECS) {
            AbilityFactory.register(spec.descriptor(), InspiredAbility.class,
                    participant -> new InspiredAbility(participant, spec));
        }
    }

    public static List<InspiredAbilitySpec> getSpecs() {
        return SPECS;
    }

    private static InspiredAbilitySpec p(String name, Material icon, InspiredAbilitySpec.Style style,
            InspiredAbilitySpec.CrowdControlType control, int cooldown, String action, String effect) {
        return new InspiredAbilitySpec(
                new AbilityDescriptor(
                        name,
                        AbilityManifest.Species.OTHERS,
                        explain(name, style, cooldown, action, effect),
                        summarize(style, control, cooldown),
                        icon,
                        cooldown > 0 ? List.of(cooldown) : List.of()),
                style,
                control,
                cooldown,
                damageFor(style),
                rangeFor(style),
                radiusFor(style),
                controlTicks(control),
                healFor(style),
                knockbackFor(style));
    }

    private static List<String> explain(String name, InspiredAbilitySpec.Style style, int cooldown, String action,
            String effect) {
        List<String> lines = new ArrayList<>(3);
        if (style == GLASS_CANNON) {
            lines.add("§e§l[패시브 - " + name + "]");
        } else {
            lines.add("§e§l[철괴 우클릭 - " + name + "]§f §8(쿨타임: " + cooldown + "초)");
        }
        lines.add("§7" + action);
        if (effect != null && !effect.isBlank()) {
            lines.add("§7" + effect);
        }
        return List.copyOf(lines);
    }

    private static List<String> summarize(InspiredAbilitySpec.Style style,
            InspiredAbilitySpec.CrowdControlType control, int cooldown) {
        List<String> lines = new ArrayList<>(3);
        lines.add((style == GLASS_CANNON ? "§7패시브§f: " : "§7철괴 우클릭§f: ") + styleName(style));
        if (control != NONE) {
            lines.add("§7CC§f: " + controlName(control));
        }
        if (cooldown > 0) {
            lines.add("§7쿨타임§f: " + cooldown + "초");
        }
        return List.copyOf(lines);
    }

    private static double damageFor(InspiredAbilitySpec.Style style) {
        return switch (style) {
            case SINGLE -> 6.8;
            case BLAST -> 4.8;
            case NOVA -> 4.4;
            case DASH -> 5.2;
            case PULL -> 3.8;
            case GUARD -> 1.8;
            case ALLY -> 1.6;
            case ASSASSIN -> 6.2;
            case BLACK_HOLE -> 3.8;
            case CURSE -> 4.0;
            case GLASS_CANNON -> 0.0;
            case SWAP -> 3.8;
            case FROST -> 3.6;
            case SOUL -> 5.2;
            case EXECUTE -> 5.8;
            case GAMBLE -> 4.0;
            case PORTAL -> 4.4;
            case MARK -> 3.4;
            case DEFLECT -> 2.8;
            case SUMMON -> 3.2;
        };
    }

    private static double rangeFor(InspiredAbilitySpec.Style style) {
        return switch (style) {
            case SINGLE, ASSASSIN, CURSE, SOUL, EXECUTE, MARK, SWAP -> 13.0;
            case PORTAL -> 9.0;
            case BLAST, BLACK_HOLE, FROST, SUMMON -> 12.0;
            case GLASS_CANNON -> 0.0;
            default -> 7.0;
        };
    }

    private static double radiusFor(InspiredAbilitySpec.Style style) {
        return switch (style) {
            case BLAST, FROST, SUMMON -> 3.8;
            case BLACK_HOLE -> 5.8;
            case NOVA, DEFLECT -> 4.2;
            case PULL, ALLY -> 6.0;
            case DASH -> 2.7;
            case GUARD -> 4.0;
            case GLASS_CANNON -> 0.0;
            default -> 0.0;
        };
    }

    private static int controlTicks(InspiredAbilitySpec.CrowdControlType control) {
        return switch (control) {
            case STUN -> 30;
            case BIND -> 54;
            case DISARM -> 48;
            case FREEZE -> 38;
            case NONE -> 0;
        };
    }

    private static double healFor(InspiredAbilitySpec.Style style) {
        return switch (style) {
            case GUARD -> 4.0;
            case ALLY -> 2.4;
            case CURSE, SOUL -> 2.0;
            case GLASS_CANNON -> 0.0;
            default -> 0.0;
        };
    }

    private static double knockbackFor(InspiredAbilitySpec.Style style) {
        return switch (style) {
            case NOVA, DEFLECT, DASH -> 0.65;
            case PULL -> 0.75;
            case GLASS_CANNON -> 0.0;
            default -> 0.45;
        };
    }

    private static String styleName(InspiredAbilitySpec.Style style) {
        return switch (style) {
            case SINGLE -> "단일 타격";
            case BLAST -> "지점 폭발";
            case NOVA -> "주변 방출";
            case DASH -> "돌진";
            case PULL -> "견인";
            case GUARD -> "방어";
            case ALLY -> "팀 지원";
            case ASSASSIN -> "기습";
            case BLACK_HOLE -> "중력장";
            case CURSE -> "저주";
            case SWAP -> "위치 교환";
            case FROST -> "빙결";
            case SOUL -> "흡혈";
            case EXECUTE -> "처형";
            case GAMBLE -> "무작위";
            case PORTAL -> "순간이동";
            case MARK -> "표식";
            case DEFLECT -> "반발";
            case SUMMON -> "전염 구역";
            case GLASS_CANNON -> "피해 증폭";
        };
    }

    private static String controlName(InspiredAbilitySpec.CrowdControlType control) {
        return switch (control) {
            case STUN -> "기절";
            case BIND -> "속박";
            case DISARM -> "무장해제";
            case FREEZE -> "빙결";
            case NONE -> "없음";
        };
    }

    private static void validate() {
        if (SPECS.size() != 100) {
            throw new IllegalStateException("InspiredAbilityPack must contain exactly 100 abilities: " + SPECS.size());
        }
        Set<String> names = new HashSet<>();
        for (InspiredAbilitySpec spec : SPECS) {
            if (!names.add(spec.descriptor().name())) {
                throw new IllegalStateException("Duplicate inspired ability name: " + spec.descriptor().name());
            }
        }
    }
}
