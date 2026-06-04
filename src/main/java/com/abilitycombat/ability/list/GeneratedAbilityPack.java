package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityDescriptor;
import com.abilitycombat.ability.AbilityFactory;
import com.abilitycombat.ability.AbilityManifest;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public final class GeneratedAbilityPack {

    private static final String[] NAMES = {
            "전위 돌파 (VanguardBreak)", "중압 강타 (PressureStrike)", "파열 창격 (RuptureLance)",
            "서리 표식 (FrostMark)", "침묵 절단 (SilentCut)", "자성 포획 (MagneticCatch)",
            "결속 파동 (BindingWave)", "분쇄 돌진 (CrushDash)", "역류 방패 (BackflowGuard)",
            "섬광 포격 (FlashBarrage)", "흑철 쇄도 (BlackIronRush)", "빙점 사슬 (ZeroChain)",
            "균열 폭심 (RiftCore)", "견제 사격 (CheckShot)", "맹독 압박 (VenomPressure)",
            "진압 명령 (SuppressOrder)", "삭풍 난격 (CuttingGale)", "철갑 반격 (IronCounter)",
            "충격 원환 (ShockRing)", "단죄 일격 (JudgementHit)", "냉기 압류 (ColdSeizure)",
            "집속 포화 (FocusFire)", "강철 포위 (SteelEncircle)", "긴급 봉쇄 (EmergencyLock)",
            "비틀림 손아귀 (TwistGrip)", "낙뢰 척결 (ThunderPurge)", "응징 보루 (PunishBulwark)",
            "혈기 돌파 (BloodRush)", "공허 속박 (VoidBind)", "역장 타격 (FieldStrike)",
            "무력화 창 (DisableSpear)", "백열 파쇄 (WhiteHeatBreak)", "압축 폭발 (CompressionBlast)",
            "폭풍 견제 (StormCheck)", "쇄빙 탄환 (IcebreakRound)", "불굴 자세 (UnyieldingStance)",
            "섬멸 호령 (AnnihilateCall)", "전류 사슬 (CurrentChain)", "돌개 진입 (WhirlEntry)",
            "중력 포박 (GravitySnare)", "잔광 참격 (AfterglowSlash)", "불꽃 압박 (FlamePress)",
            "금속 장막 (MetalCurtain)", "한기 돌풍 (ChillGust)", "정밀 진압 (PreciseSuppress)",
            "번개 궤적 (LightningTrail)", "파수 결계 (SentryWard)", "강습 표식 (AssaultMark)",
            "수평 절단 (LevelCut)", "빙하 쇄도 (GlacierSurge)", "격류 견인 (TorrentPull)",
            "철심 난타 (IronCoreBeat)", "침식 파동 (ErosionWave)", "회전 압살 (SpinCrush)",
            "일점 봉쇄 (PointLock)", "푸른 포화 (BlueBarrage)", "붉은 돌진 (RedCharge)",
            "황금 저지 (GoldenStop)", "유성 압박 (MeteorPress)", "침잠 사슬 (SinkChain)",
            "신속 제압 (SwiftSubdue)", "환영 파열 (PhantomBurst)", "저항 태세 (ResistForm)",
            "냉혈 추격 (ColdChase)", "감전 결박 (StaticBind)", "포식 장악 (PredatorHold)",
            "잔혹 방출 (CruelRelease)", "진공 포격 (VacuumShot)", "파괴 선고 (BreakSentence)",
            "수호 반경 (GuardRadius)", "서리 격발 (FrostTrigger)", "단절 충격 (SeverShock)",
            "광휘 돌파 (RadiantBreak)", "묵직한 손 (HeavyHand)", "전술 견인 (TacticalPull)",
            "파동 차단 (WaveBlock)", "불꽃 사슬 (FlameChain)", "광역 진압 (WideSuppress)",
            "축전 강타 (ChargeSmash)", "어둠 결박 (DarkBind)", "청색 창격 (AzureLance)",
            "회복 보루 (RecoveryBastion)", "압도 진입 (OverwhelmEntry)", "공명 폭발 (ResonanceBlast)",
            "비상 봉쇄 (EmergencySeal)", "강철 쇠약 (SteelWeaken)", "빙결 명령 (FreezeCommand)",
            "충돌 제어 (ImpactControl)", "섬광 구속 (FlashBind)", "전선 유지 (LineHold)",
            "응축 타격 (CondensedHit)", "냉각 파장 (CoolingPulse)", "무장 분쇄 (ArmBreak)",
            "돌파 명령 (BreakthroughOrder)", "포위 압력 (SiegePressure)", "추적 결계 (ChaseWard)",
            "집중 저격 (FocusSnipe)", "파멸 권역 (RuinZone)", "정지 선율 (StopRhythm)",
            "전략 제압 (StrategicSubdue)"
    };

    private static final Material[] ICONS = {
            Material.IRON_SWORD, Material.IRON_AXE, Material.TRIDENT, Material.BLUE_ICE, Material.SHEARS,
            Material.CHAIN, Material.IRON_INGOT, Material.SHIELD, Material.CROSSBOW, Material.LIGHTNING_ROD,
            Material.AMETHYST_SHARD, Material.ENDER_PEARL, Material.REDSTONE, Material.FIRE_CHARGE,
            Material.COPPER_INGOT, Material.ECHO_SHARD, Material.PRISMARINE_SHARD, Material.GOLD_INGOT
    };

    private static final int ATTACK_COUNT = 60;
    private static final int DEFENSE_COUNT = 20;
    private static final int SUPPORT_COUNT = 20;
    private static final GeneratedAbilitySpec.Pattern[] ATTACK_PATTERNS = {
            GeneratedAbilitySpec.Pattern.STRIKE,
            GeneratedAbilitySpec.Pattern.DASH,
            GeneratedAbilitySpec.Pattern.BLAST,
            GeneratedAbilitySpec.Pattern.NOVA,
            GeneratedAbilitySpec.Pattern.STRIKE,
            GeneratedAbilitySpec.Pattern.DASH,
            GeneratedAbilitySpec.Pattern.PULL,
            GeneratedAbilitySpec.Pattern.BLAST,
            GeneratedAbilitySpec.Pattern.NOVA,
            GeneratedAbilitySpec.Pattern.STRIKE
    };
    private static final GeneratedAbilitySpec.Pattern[] DEFENSE_PATTERNS = {
            GeneratedAbilitySpec.Pattern.GUARD,
            GeneratedAbilitySpec.Pattern.GUARD,
            GeneratedAbilitySpec.Pattern.NOVA,
            GeneratedAbilitySpec.Pattern.GUARD,
            GeneratedAbilitySpec.Pattern.DASH,
            GeneratedAbilitySpec.Pattern.GUARD,
            GeneratedAbilitySpec.Pattern.PULL,
            GeneratedAbilitySpec.Pattern.GUARD,
            GeneratedAbilitySpec.Pattern.NOVA,
            GeneratedAbilitySpec.Pattern.GUARD
    };
    private static final GeneratedAbilitySpec.Pattern[] SUPPORT_PATTERNS = {
            GeneratedAbilitySpec.Pattern.PULL,
            GeneratedAbilitySpec.Pattern.NOVA,
            GeneratedAbilitySpec.Pattern.BLAST,
            GeneratedAbilitySpec.Pattern.GUARD,
            GeneratedAbilitySpec.Pattern.STRIKE,
            GeneratedAbilitySpec.Pattern.PULL,
            GeneratedAbilitySpec.Pattern.NOVA,
            GeneratedAbilitySpec.Pattern.BLAST,
            GeneratedAbilitySpec.Pattern.GUARD,
            GeneratedAbilitySpec.Pattern.DASH
    };
    private static final GeneratedAbilitySpec.CrowdControlType[] CONTROLS = {
            GeneratedAbilitySpec.CrowdControlType.STUN,
            GeneratedAbilitySpec.CrowdControlType.BIND,
            GeneratedAbilitySpec.CrowdControlType.DISARM,
            GeneratedAbilitySpec.CrowdControlType.FREEZE,
            GeneratedAbilitySpec.CrowdControlType.NONE
    };
    private static final List<GeneratedAbilitySpec> SPECS = buildSpecs();

    private GeneratedAbilityPack() {
    }

    public static void registerAll() {
        for (GeneratedAbilitySpec spec : SPECS) {
            AbilityFactory.register(spec.descriptor(), GeneratedAbility.class,
                    participant -> new GeneratedAbility(participant, spec));
        }
    }

    public static List<GeneratedAbilitySpec> getSpecs() {
        return SPECS;
    }

    private static List<GeneratedAbilitySpec> buildSpecs() {
        List<GeneratedAbilitySpec> specs = new ArrayList<>(NAMES.length);
        for (int index = 0; index < NAMES.length; index++) {
            GeneratedAbilitySpec.Role role = roleFor(index);
            int roleIndex = roleIndex(index, role);
            GeneratedAbilitySpec.Pattern pattern = patternFor(role, roleIndex);
            GeneratedAbilitySpec.CrowdControlType control = CONTROLS[(roleIndex + index / 20) % CONTROLS.length];
            int cooldown = cooldownFor(role, roleIndex);
            double damage = baseDamage(role, pattern) + (roleIndex % 4) * 0.25;
            if (control == GeneratedAbilitySpec.CrowdControlType.STUN
                    || control == GeneratedAbilitySpec.CrowdControlType.FREEZE) {
                damage -= role == GeneratedAbilitySpec.Role.ATTACK ? 0.65 : 0.35;
            }
            double range = 11.0 + (index % 5) * 1.5;
            double radius = baseRadius(role, pattern) + (roleIndex % 3) * 0.3;
            int controlTicks = controlTicks(control) + (cooldown > 45 ? 10 : 0);
            double heal = healFor(role, pattern, roleIndex);
            double knockback = knockbackFor(pattern);
            String displayName = displayName(NAMES[index]);
            AbilityDescriptor descriptor = new AbilityDescriptor(
                    NAMES[index],
                    AbilityManifest.Species.OTHERS,
                    explain(displayName, role, pattern, control, cooldown, damage, radius, range, controlTicks, heal),
                    summarize(role, pattern, control, cooldown),
                    ICONS[index % ICONS.length],
                    List.of(cooldown));
            specs.add(new GeneratedAbilitySpec(
                    descriptor, role, pattern, control, cooldown, Math.max(0.0, damage), range, radius,
                    controlTicks, heal, knockback));
        }
        return List.copyOf(specs);
    }

    private static GeneratedAbilitySpec.Role roleFor(int index) {
        if (index < ATTACK_COUNT) {
            return GeneratedAbilitySpec.Role.ATTACK;
        }
        if (index < ATTACK_COUNT + DEFENSE_COUNT) {
            return GeneratedAbilitySpec.Role.DEFENSE;
        }
        return GeneratedAbilitySpec.Role.SUPPORT;
    }

    private static int roleIndex(int index, GeneratedAbilitySpec.Role role) {
        return switch (role) {
            case ATTACK -> index;
            case DEFENSE -> index - ATTACK_COUNT;
            case SUPPORT -> index - ATTACK_COUNT - DEFENSE_COUNT;
        };
    }

    private static GeneratedAbilitySpec.Pattern patternFor(GeneratedAbilitySpec.Role role, int roleIndex) {
        return switch (role) {
            case ATTACK -> ATTACK_PATTERNS[roleIndex % ATTACK_PATTERNS.length];
            case DEFENSE -> DEFENSE_PATTERNS[roleIndex % DEFENSE_PATTERNS.length];
            case SUPPORT -> SUPPORT_PATTERNS[roleIndex % SUPPORT_PATTERNS.length];
        };
    }

    private static int cooldownFor(GeneratedAbilitySpec.Role role, int roleIndex) {
        return switch (role) {
            case ATTACK -> 20 + ((roleIndex * 7) % 41);
            case DEFENSE -> 24 + ((roleIndex * 9) % 37);
            case SUPPORT -> 18 + ((roleIndex * 8) % 35);
        };
    }

    private static double baseDamage(GeneratedAbilitySpec.Role role, GeneratedAbilitySpec.Pattern pattern) {
        return switch (role) {
            case ATTACK -> switch (pattern) {
                case STRIKE -> 6.2;
                case BLAST -> 4.4;
                case NOVA -> 3.8;
                case DASH -> 5.4;
                case GUARD -> 0.0;
                case PULL -> 3.8;
            };
            case DEFENSE -> switch (pattern) {
                case STRIKE -> 3.1;
                case BLAST -> 2.7;
                case NOVA -> 2.4;
                case DASH -> 3.0;
                case GUARD -> 0.0;
                case PULL -> 2.0;
            };
            case SUPPORT -> switch (pattern) {
                case STRIKE -> 2.8;
                case BLAST -> 2.4;
                case NOVA -> 2.0;
                case DASH -> 2.8;
                case GUARD -> 0.0;
                case PULL -> 1.8;
            };
        };
    }

    private static double baseRadius(GeneratedAbilitySpec.Role role, GeneratedAbilitySpec.Pattern pattern) {
        double radius = switch (pattern) {
            case STRIKE -> 0.0;
            case BLAST -> 3.2;
            case NOVA -> 4.2;
            case DASH -> 2.6;
            case GUARD -> 4.0;
            case PULL -> 6.0;
        };
        return role == GeneratedAbilitySpec.Role.SUPPORT ? Math.max(radius, 5.0) : radius;
    }

    private static double healFor(GeneratedAbilitySpec.Role role, GeneratedAbilitySpec.Pattern pattern, int roleIndex) {
        return switch (role) {
            case ATTACK -> pattern == GeneratedAbilitySpec.Pattern.DASH && roleIndex % 2 == 0 ? 1.2 : 0.0;
            case DEFENSE -> 4.0 + (roleIndex % 3);
            case SUPPORT -> pattern == GeneratedAbilitySpec.Pattern.GUARD ? 2.0 : 0.0;
        };
    }

    private static double knockbackFor(GeneratedAbilitySpec.Pattern pattern) {
        return switch (pattern) {
            case PULL -> 0.65;
            case NOVA, DASH -> 0.55;
            default -> 0.0;
        };
    }

    private static int controlTicks(GeneratedAbilitySpec.CrowdControlType control) {
        return switch (control) {
            case STUN -> 32;
            case BIND -> 54;
            case DISARM -> 48;
            case FREEZE -> 38;
            case NONE -> 0;
        };
    }

    private static List<String> explain(String displayName, GeneratedAbilitySpec.Role role,
            GeneratedAbilitySpec.Pattern pattern,
            GeneratedAbilitySpec.CrowdControlType control, int cooldown, double damage, double radius, double range,
            int controlTicks, double heal) {
        List<String> lines = new ArrayList<>();
        lines.add("§e§l[철괴 우클릭 - " + displayName + "]§f §8(쿨타임: " + cooldown + "초)");
        lines.add(patternLine(role, pattern, damage, radius, range));
        lines.add(effectLine(role, control, controlTicks, heal));
        return List.copyOf(lines);
    }

    private static List<String> summarize(GeneratedAbilitySpec.Role role, GeneratedAbilitySpec.Pattern pattern,
            GeneratedAbilitySpec.CrowdControlType control, int cooldown) {
        List<String> lines = new ArrayList<>();
        lines.add("§7분류§f: " + roleName(role));
        lines.add("§7철괴 우클릭§f: " + patternName(pattern));
        if (control != GeneratedAbilitySpec.CrowdControlType.NONE) {
            lines.add("§7CC§f: " + controlName(control));
        }
        lines.add("§7쿨타임§f: " + cooldown + "초");
        return List.copyOf(lines);
    }

    private static String patternLine(GeneratedAbilitySpec.Role role, GeneratedAbilitySpec.Pattern pattern,
            double damage, double radius, double range) {
        return switch (pattern) {
            case STRIKE -> "§7바라본 적에게 피해 §c" + formatOne(damage) + "§7를 줍니다.";
            case BLAST -> "§7바라본 지점 주변 §f" + formatOne(radius) + "칸§7에 피해 §c" + formatOne(damage) + "§7.";
            case NOVA -> "§7자신 주변 §f" + formatOne(radius) + "칸§7 적을 밀치며 피해 §c" + formatOne(damage) + "§7.";
            case DASH -> "§7바라보는 방향으로 진입하고 주변 적에게 피해 §c" + formatOne(damage) + "§7.";
            case GUARD -> role == GeneratedAbilitySpec.Role.SUPPORT
                    ? "§7주변 팀원을 강화하고 가까운 적을 방해합니다."
                    : "§7짧게 방어 태세를 갖추고 주변 적을 방해합니다.";
            case PULL -> "§7주변 §f" + formatOne(radius) + "칸§7 적을 끌어당기며 피해 §c" + formatOne(damage) + "§7.";
        };
    }

    private static String effectLine(GeneratedAbilitySpec.Role role, GeneratedAbilitySpec.CrowdControlType control,
            int controlTicks, double heal) {
        List<String> parts = new ArrayList<>();
        if (role == GeneratedAbilitySpec.Role.DEFENSE) {
            parts.add("군중제어 해제");
            parts.add("체력 " + formatOne(heal) + " 회복");
        } else if (role == GeneratedAbilitySpec.Role.SUPPORT) {
            parts.add("주변 팀원 신속/재생");
            if (heal > 0.0) {
                parts.add("체력 " + formatOne(heal) + " 회복");
            }
        } else if (heal > 0.0) {
            parts.add("체력 " + formatOne(heal) + " 회복");
        }
        if (control != GeneratedAbilitySpec.CrowdControlType.NONE) {
            parts.add("적중 대상 " + controlName(control) + " " + formatSeconds(controlTicks));
        }
        if (control == GeneratedAbilitySpec.CrowdControlType.FREEZE) {
            parts.add("빙결 대상 타격 시 추가 회복");
        }
        if (parts.isEmpty()) {
            parts.add("적중 시 짧은 압박");
        }
        return "§7" + String.join(" / ", parts) + ".";
    }

    private static String roleName(GeneratedAbilitySpec.Role role) {
        return switch (role) {
            case ATTACK -> "공격";
            case DEFENSE -> "방어";
            case SUPPORT -> "지원";
        };
    }

    private static String patternName(GeneratedAbilitySpec.Pattern pattern) {
        return switch (pattern) {
            case STRIKE -> "단일 타격";
            case BLAST -> "지점 폭발";
            case NOVA -> "주변 방출";
            case DASH -> "돌진";
            case GUARD -> "방어/방해";
            case PULL -> "견인";
        };
    }

    private static String controlName(GeneratedAbilitySpec.CrowdControlType control) {
        return switch (control) {
            case STUN -> "기절";
            case BIND -> "속박";
            case DISARM -> "무장해제";
            case FREEZE -> "빙결";
            case NONE -> "없음";
        };
    }

    private static String displayName(String name) {
        int index = name.indexOf(" (");
        return index > 0 ? name.substring(0, index) : name;
    }

    private static String formatSeconds(int ticks) {
        return formatOne(ticks / 20.0) + "초";
    }

    private static String formatOne(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
