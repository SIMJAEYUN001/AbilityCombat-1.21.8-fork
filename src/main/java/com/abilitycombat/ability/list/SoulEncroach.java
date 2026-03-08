package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.LocationUtil;
import com.abilitycombat.utils.ParticleUtil;
import com.abilitycombat.vfx.Points;
import com.abilitycombat.vfx.VectorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@AbilityManifest(name = "영혼 잠식 (SoulEncroach)", rank = AbilityManifest.Rank.S, species = AbilityManifest.Species.GOD, explain = {
                "§e§l[철괴 우클릭 - 잠식]§f §8(쿨타임: 60초)",
                "§f7칸§7 이내, 마지막으로 타격한 대상에게 잠식합니다.",
                "§f3초§7간 §b무적§7, §b비가시§7, §b비행§7 상태로 대상을 따라다닙니다.",
                "",
                "§e§l[웅크리기 - 잠식 해제]",
                "§7잠식을 해제하며 대상에게 순간이동합니다.",
                "§7잠식 중 대상의 §c체력이 적을수록§7 추가 피해를 줍니다.",
                "",
                "§7이 피해로 대상 §c처치§7 시 체력 50%를 회복하고 쿨타임이 초기화됩니다."
}, summarize = {
                "§7철괴 우클릭§f: 3초 잠식 → 잃은 체력 피해",
                "§7처치 시§f: 회복 + 쿨타임 초기화"
})
public class SoulEncroach extends AbilityBase implements ActiveHandler {

        private static final int COOLDOWN_SECONDS = 60;
        private static final int ENCROACH_SECONDS = 3;
        private static final double RANGE = 7.0;
        private static final int TICK_PERIOD = 2;
        private static final double DAMAGE_BASE = 21.5;
        private static final double KILL_BONUS = 0.15;
        private static final int PARTICLE_INTERVAL_TICKS = 6;
        private static final String NOTICE_KEY = "soul_encroach_notice";
        private static final DustOptions WHITE_DUST = new DustOptions(Color.fromRGB(250, 250, 250), 0.6f);
        private static final DustOptions BLACK_DUST = new DustOptions(Color.fromRGB(10, 10, 10), 0.6f);
        private static final List<Vector> WHITE_LAYER = Points.of(0.06, new boolean[][] {
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, true,
                                        true, true, true, true, true, true, true, true, true, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, true,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, true, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, true, true, true,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, true, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, true, true, true, true,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, true, true, false,
                                        false, false, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, true, true, true, true, true,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, true, true, true,
                                        false, false, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, true, true, true, true, true,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, true, true, true,
                                        false, false, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, true, true, true, true, true, true,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, true, true, true,
                                        true, false, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, true, true, true, true, true, true,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, true, true, true,
                                        true, false, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, true, true, true, true, true, true,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, true, true, true,
                                        true, false, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, true, true, true, true, true, true, false,
                                        false, true,
                                        true, true, true, true, true, true, true, false, false, true, true, true, true,
                                        true, true, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, true, true, true, true, true, true, false,
                                        false, false,
                                        true, true, true, true, true, true, true, false, false, false, true, true, true,
                                        true, true, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, true, true, true, true, true, false, false,
                                        false, false,
                                        true, true, true, true, true, true, false, false, false, false, true, true,
                                        true, true, true, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, true, true, true, true, true, false, false,
                                        false, false,
                                        true, true, true, true, true, true, false, false, false, false, true, true,
                                        true, true, true, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, true, true, true, true, true, false, false,
                                        false, false,
                                        true, true, true, true, true, true, false, false, false, false, true, true,
                                        true, true, true, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, true, true, true, true, true, true, false,
                                        false, false,
                                        true, true, true, true, true, true, true, false, false, false, true, true, true,
                                        true, true, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, true, true, true, true, true, true, true,
                                        true, true,
                                        true, true, false, false, false, false, true, true, true, true, true, true,
                                        true, true, true, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, true, true, true, true, true, true, true,
                                        true, true,
                                        true, false, false, false, false, false, true, true, true, true, true, true,
                                        true, true, true,
                                        false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, true, true, true, true, true, true, true,
                                        true, true,
                                        true, false, false, false, false, false, true, true, true, true, true, true,
                                        true, true, true,
                                        false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, true, true, true, true, true, true,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, true, true, true,
                                        true, false, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, true, true, true, true, true,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, true, true, true,
                                        false, false, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, true, true, true,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, true, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, true, true, true, true, true, true, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false }
        });
        private static final List<Vector> BLACK_LAYER = Points.of(0.06, new boolean[][] {
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, true,
                                        true, true, true, true, true, true, true, true, true, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, true,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, true, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, true, true, true,
                                        true,
                                        false, false, false, false, false, false, false, false, true, true, true, true,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, true, true, true, false,
                                        false,
                                        false, false, false, false, false, false, false, false, false, false, true,
                                        true, true, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, true, true, true, false, false,
                                        false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        true, true, true,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, true, true, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, true, true,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, true, true, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, true,
                                        true, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, true, true, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, true,
                                        true, true, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        true, true, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, true, false, false, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, false,
                                        true, true, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, true, true, true, false, false, false, true, true,
                                        true, true,
                                        false, false, false, false, false, false, true, true, true, true, false, false,
                                        false, true, true,
                                        true, false, false, false, false, false, false },
                        { false, false, false, false, false, false, true, true, false, false, false, true, true, true,
                                        true, true,
                                        true, false, false, false, false, true, true, true, true, true, true, false,
                                        false, false, true,
                                        true, false, false, false, false, false, false },
                        { false, false, false, false, false, false, true, true, false, false, false, true, true, true,
                                        true, true,
                                        true, false, false, false, false, true, true, true, true, true, true, false,
                                        false, false, true,
                                        true, false, false, false, false, false, false },
                        { false, false, false, false, false, false, true, true, false, false, false, true, true, true,
                                        true, true,
                                        true, false, false, false, false, true, true, true, true, true, true, false,
                                        false, false, true,
                                        true, false, false, false, false, false, false },
                        { false, false, false, false, false, false, true, true, false, false, false, true, true, true,
                                        true, true,
                                        true, false, false, false, false, true, true, true, true, true, true, false,
                                        false, false, true,
                                        true, false, false, false, false, false, false },
                        { false, false, false, false, false, false, true, true, false, false, false, true, true, true,
                                        true, true,
                                        true, false, false, false, false, true, true, true, true, true, true, false,
                                        false, false, true,
                                        true, false, false, false, false, false, false },
                        { false, false, false, false, false, false, true, true, false, false, false, false, false, true,
                                        true,
                                        false, false, false, false, false, false, false, false, true, true, false,
                                        false, false, false,
                                        false, true, true, false, false, false, false, false, false },
                        { false, false, false, false, false, false, true, true, false, false, false, false, false,
                                        false, false,
                                        false, false, true, true, true, true, false, false, false, false, false, false,
                                        false, false, false,
                                        true, true, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, true, true, false, false, false, false,
                                        false, false,
                                        false, true, true, false, false, true, true, false, false, false, false, false,
                                        false, false, true,
                                        true, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, true, true, true, false, false, false, false,
                                        false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        false, false, true,
                                        true, true, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, true, true, true, true, false, false,
                                        false,
                                        false, false, false, false, false, false, false, false, false, false, false,
                                        true, true, true, true,
                                        false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, true, true, true, true, true,
                                        false, false,
                                        false, false, false, false, false, false, false, false, true, true, true, true,
                                        true, false, false,
                                        false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, true, true, true,
                                        false,
                                        false, false, false, false, false, false, false, false, false, true, true, true,
                                        false, false,
                                        false, false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, true,
                                        true, false,
                                        true, true, false, true, true, false, true, true, false, true, true, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, true,
                                        true, true,
                                        true, true, false, true, true, false, true, true, true, true, true, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        true, true,
                                        true, true, true, true, true, true, true, true, true, true, false, false, false,
                                        false, false,
                                        false, false, false, false, false, false, false, false },
                        { false, false, false, false, false, false, false, false, false, false, false, false, false,
                                        false, true,
                                        true, true, true, true, true, true, true, true, true, false, false, false,
                                        false, false, false,
                                        false, false, false, false, false, false, false, false }
        });

        private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
        private UUID lastHitTarget;
        private LivingEntity target;
        private int killCount;
        private int particleTick;
        private boolean storedAllowFlight;
        private boolean storedFlying;
        private boolean storedInvulnerable;
        private boolean storedInvisible;
        private boolean storedCollidable;
        private boolean encroaching;
        private int remainingEncroachTicks = 0;

        public SoulEncroach(Participant participant) {
                super(participant);
        }

        @Override
        protected void onActivate() {
                registerTick();
                subscribeEvent(EntityDamageByEntityEvent.class);
                subscribeEvent(PlayerToggleSneakEvent.class);
        }

        @Override
        protected void onDeactivate() {
                unregisterTick();
                exitEncroach(false);
                clearNotice();
        }

        @Override
        public boolean activeSkill(Material material, ClickType clickType) {
                if (material != Material.IRON_INGOT || clickType != ClickType.RIGHT_CLICK) {
                        return false;
                }
                if (cooldown.isCooldown()) {
                        notifyCooldown(cooldown);
                        return false;
                }
                if (isEncroaching()) {
                        return false;
                }
                Player player = getPlayer();
                LivingEntity last = resolveLastHitTarget();
                if (last == null) {
                        return false;
                }
                if (player.getLocation().distanceSquared(last.getLocation()) > RANGE * RANGE) {
                        return false;
                }
                target = last;
                cooldown.start();
                applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
                startEncroach();
                return true;
        }

        @Override
        public void handleBridgeEvent(Event event) {
                if (event instanceof EntityDamageByEntityEvent) {
                        onDamageByEntity((EntityDamageByEntityEvent) event);
                } else if (event instanceof PlayerToggleSneakEvent) {
                        onSneak((PlayerToggleSneakEvent) event);
                }
        }

        private void onDamageByEntity(EntityDamageByEntityEvent event) {
                if (!(event.getDamager() instanceof Player player) || !player.equals(getPlayer())) {
                        return;
                }
                if (event.getEntity() instanceof LivingEntity living && !living.equals(player)) {
                        lastHitTarget = living.getUniqueId();
                }
        }

        private void onSneak(PlayerToggleSneakEvent event) {
                if (!event.getPlayer().equals(getPlayer())) {
                        return;
                }
                if (!event.isSneaking()) {
                        return;
                }
                if (isEncroaching()) {
                        stopEncroach(false);
                }
        }

        @Override
        protected void onDestroy() {
                unregisterTick();
                exitEncroach(false);
                clearNotice();
        }

        private LivingEntity resolveLastHitTarget() {
                if (lastHitTarget == null) {
                        return null;
                }
                if (getPlayer().getWorld() == null) {
                        return null;
                }
                for (LivingEntity living : getPlayer().getWorld().getLivingEntities()) {
                        if (living.getUniqueId().equals(lastHitTarget)) {
                                if (!com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), living)) {
                                        return null;
                                }
                                return living;
                        }
                }
                return null;
        }

        private void enterEncroach() {
                Player player = getPlayer();
                storedAllowFlight = player.getAllowFlight();
                storedFlying = player.isFlying();
                storedInvulnerable = player.isInvulnerable();
                storedInvisible = player.isInvisible();
                storedCollidable = player.isCollidable();
                encroaching = true;
                player.setInvulnerable(true);
                player.setInvisible(true);
                player.setCollidable(false);
                player.setAllowFlight(true);
                player.setFlying(true);
        }

        private void exitEncroach(boolean applyDamage) {
                if (!encroaching) {
                        return;
                }
                Player player = getPlayer();
                player.setInvulnerable(storedInvulnerable);
                player.setInvisible(storedInvisible);
                player.setCollidable(storedCollidable);
                player.setAllowFlight(storedAllowFlight);
                player.setFlying(storedFlying);

                if (applyDamage && target != null && !target.isDead()) {
                        player.teleport(target.getLocation());
                        double damage = getDamage(target);
                        target.damage(damage, player);
                        scheduleKillCheck(target);
                }
                target = null;
                encroaching = false;
        }

        private void scheduleKillCheck(LivingEntity victim) {
                if (victim == null) {
                        return;
                }
                AbilityCombat.getPlugin().getServer().getScheduler().runTaskLater(AbilityCombat.getPlugin(), () -> {
                        if (victim.isDead() || victim.getHealth() <= 0.0) {
                                handleKill(victim);
                        } else {
                                killCount = 0;
                        }
                }, 1L);
        }

        private void handleKill(LivingEntity victim) {
                killCount++;
                Player player = getPlayer();
                double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                double heal = maxHealth / 2.0;
                player.setHealth(Math.min(maxHealth, player.getHealth() + heal));
                cooldown.stop(true);
                LivingEntity next = findNextTarget(victim);
                lastHitTarget = next != null ? next.getUniqueId() : null;
        }

        private void followTarget() {
                if (target == null || target.isDead()) {
                        stopEncroach(false);
                        return;
                }
                Location headLocation = target.getEyeLocation().clone().add(0, 1.5, 0);
                getPlayer().teleport(headLocation);
                particleTick += TICK_PERIOD;
                if (particleTick >= PARTICLE_INTERVAL_TICKS) {
                        particleTick = 0;
                        spawnSkullParticles(headLocation, target.getLocation().getYaw());
                }
        }

        private double getDamage(LivingEntity victim) {
                if (victim == null) {
                        return 0.0;
                }
                double maxHealth = getMaxHealth(victim);
                if (maxHealth <= 0.0) {
                        return 0.0;
                }
                double ratio = 1.0 - (victim.getHealth() / maxHealth);
                return Math.max(1.0, DAMAGE_BASE * ratio * (1.0 + (killCount * KILL_BONUS)));
        }

        private double getMaxHealth(LivingEntity target) {
                AttributeInstance instance = target.getAttribute(Attribute.MAX_HEALTH);
                return instance != null ? instance.getValue() : 20.0;
        }

        private void updateNotice() {
                LivingEntity last = resolveLastHitTarget();
                if (last == null || last.isDead()) {
                        clearNotice();
                        return;
                }
                if (getDamage(last) >= last.getHealth()) {
                        var channel = getActionbarChannel();
                        if (channel != null) {
                                channel.update(getPlayer(), NOTICE_KEY, 5,
                                                Component.text("마지막으로 때린 상대의 체력이 적습니다.", NamedTextColor.RED));
                        }
                } else {
                        clearNotice();
                }
        }

        private void clearNotice() {
                var channel = getActionbarChannel();
                if (channel != null) {
                        channel.clear(getPlayer(), NOTICE_KEY);
                }
        }

        private LivingEntity findNextTarget(LivingEntity exclude) {
                Player player = getPlayer();
                Predicate<LivingEntity> predicate = entity -> !entity.equals(player)
                                && (exclude == null || !entity.equals(exclude));
                return LocationUtil.getNearestEntity(LivingEntity.class, player.getLocation(), RANGE, predicate);
        }

        private void spawnSkullParticles(Location headLocation, float yaw) {
                Location base = headLocation.clone().subtract(0, 1.4, 0);
                double radians = Math.toRadians(-yaw);
                spawnLayer(base, WHITE_LAYER, radians, WHITE_DUST);
                spawnLayer(base, BLACK_LAYER, radians, BLACK_DUST);
        }

        private void spawnLayer(Location base, List<Vector> points, double radians, DustOptions options) {
                if (points.isEmpty()) {
                        return;
                }
                for (Vector vector : points) {
                        Vector rotated = VectorUtil.rotateAroundAxisY(vector.clone(), radians);
                        Location loc = base.clone().add(rotated);
                        ParticleUtil.spawnParticle(base.getWorld(), Particle.DUST, loc, 1, 0, 0, 0, 0, options, 2, 0);
                }
        }

        private void startEncroach() {
                remainingEncroachTicks = ENCROACH_SECONDS * (20 / TICK_PERIOD);
                enterEncroach();
                registerTick();
        }

        private void stopEncroach(boolean applyDamage) {
                exitEncroach(applyDamage);
                remainingEncroachTicks = 0;
        }

        private boolean isEncroaching() {
                return remainingEncroachTicks > 0;
        }

        @Override
        public void onTick(int tick) {
                if (isDestroyed()) {
                        unregisterTick();
                        return;
                }

                // Encroach Logic (Every 2 ticks)
                if (tick % TICK_PERIOD == 0) {
                        if (isEncroaching()) {
                                followTarget();
                                remainingEncroachTicks--;
                                if (remainingEncroachTicks <= 0) {
                                        stopEncroach(true);
                                }
                        }
                }

                // Notice Logic (Every 3 ticks)
                if (tick % 3 == 0) {
                        updateNotice();
                }
        }
}
