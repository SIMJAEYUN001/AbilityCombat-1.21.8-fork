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

    private static final GeneratedAbilitySpec.Pattern[] PATTERNS = GeneratedAbilitySpec.Pattern.values();
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
            GeneratedAbilitySpec.Pattern pattern = PATTERNS[index % PATTERNS.length];
            GeneratedAbilitySpec.CrowdControlType control = CONTROLS[index % CONTROLS.length];
            int cooldown = 18 + ((index * 7) % 43);
            double damage = baseDamage(pattern) + (index % 4) * 0.35;
            if (control == GeneratedAbilitySpec.CrowdControlType.STUN
                    || control == GeneratedAbilitySpec.CrowdControlType.FREEZE) {
                damage -= 0.75;
            }
            double range = 11.0 + (index % 5) * 1.5;
            double radius = baseRadius(pattern) + (index % 3) * 0.35;
            int controlTicks = controlTicks(control) + (cooldown > 45 ? 10 : 0);
            double heal = pattern == GeneratedAbilitySpec.Pattern.GUARD ? 4.0 + (index % 3)
                    : (pattern == GeneratedAbilitySpec.Pattern.DASH && index % 2 == 0 ? 1.5 : 0.0);
            double knockback = pattern == GeneratedAbilitySpec.Pattern.PULL ? 0.65
                    : (pattern == GeneratedAbilitySpec.Pattern.NOVA || pattern == GeneratedAbilitySpec.Pattern.DASH ? 0.55 : 0.0);
            String displayName = displayName(NAMES[index]);
            AbilityDescriptor descriptor = new AbilityDescriptor(
                    NAMES[index],
                    null,
                    AbilityManifest.Species.OTHERS,
                    explain(displayName, pattern, control, cooldown, damage, radius, range, controlTicks, heal),
                    summarize(pattern, control, cooldown),
                    ICONS[index % ICONS.length],
                    List.of(cooldown));
            specs.add(new GeneratedAbilitySpec(
                    descriptor, pattern, control, cooldown, Math.max(0.0, damage), range, radius,
                    controlTicks, heal, knockback));
        }
        return List.copyOf(specs);
    }

    private static double baseDamage(GeneratedAbilitySpec.Pattern pattern) {
        return switch (pattern) {
            case STRIKE -> 6.2;
            case BLAST -> 4.3;
            case NOVA -> 3.8;
            case DASH -> 5.0;
            case GUARD -> 0.0;
            case PULL -> 3.2;
        };
    }

    private static double baseRadius(GeneratedAbilitySpec.Pattern pattern) {
        return switch (pattern) {
            case STRIKE -> 0.0;
            case BLAST -> 3.2;
            case NOVA -> 4.2;
            case DASH -> 2.6;
            case GUARD -> 4.0;
            case PULL -> 6.0;
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

    private static List<String> explain(String displayName, GeneratedAbilitySpec.Pattern pattern,
            GeneratedAbilitySpec.CrowdControlType control, int cooldown, double damage, double radius, double range,
            int controlTicks, double heal) {
        List<String> lines = new ArrayList<>();
        lines.add("§e§l[철괴 우클릭 - " + displayName + "]§f §8(쿨타임: " + cooldown + "초)");
        lines.add(patternLine(pattern, damage, radius, range));
        if (control != GeneratedAbilitySpec.CrowdControlType.NONE) {
            lines.add("§7적중 대상에게 " + controlName(control) + " §f" + formatSeconds(controlTicks) + "§7 적용.");
        }
        if (heal > 0.0) {
            lines.add("§7시전 시 체력 §a" + formatOne(heal) + "§7 회복.");
        }
        return List.copyOf(lines);
    }

    private static List<String> summarize(GeneratedAbilitySpec.Pattern pattern,
            GeneratedAbilitySpec.CrowdControlType control, int cooldown) {
        List<String> lines = new ArrayList<>();
        lines.add("§7철괴 우클릭§f: " + patternName(pattern));
        if (control != GeneratedAbilitySpec.CrowdControlType.NONE) {
            lines.add("§7CC§f: " + controlName(control));
        }
        lines.add("§7쿨타임§f: " + cooldown + "초");
        return List.copyOf(lines);
    }

    private static String patternLine(GeneratedAbilitySpec.Pattern pattern, double damage, double radius, double range) {
        return switch (pattern) {
            case STRIKE -> "§7바라본 적에게 피해 §c" + formatOne(damage) + "§7를 줍니다.";
            case BLAST -> "§7바라본 지점 주변 §f" + formatOne(radius) + "칸§7에 피해 §c" + formatOne(damage) + "§7.";
            case NOVA -> "§7자신 주변 §f" + formatOne(radius) + "칸§7 적을 밀치며 피해 §c" + formatOne(damage) + "§7.";
            case DASH -> "§7바라보는 방향으로 진입하고 주변 적에게 피해 §c" + formatOne(damage) + "§7.";
            case GUARD -> "§7짧게 방어 태세를 갖추고 주변 적을 방해합니다.";
            case PULL -> "§7주변 §f" + formatOne(radius) + "칸§7 적을 끌어당기며 피해 §c" + formatOne(damage) + "§7.";
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
