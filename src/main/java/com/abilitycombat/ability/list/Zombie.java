package com.abilitycombat.ability.list;

import com.abilitycombat.AbilityCombat;
import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.effect.Infection;
import com.abilitycombat.game.Participant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@AbilityManifest(name = "좀비 (Zombie)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.UNDEAD, explain = {
        "§e§l[패시브 - 동족]",
        "§7좀비 몬스터가 자신을 공격하지 않습니다.",
        "",
        "§e§l[철괴 우클릭 - 감염된 무리]§f §8(쿨타임: 50초)",
        "§7§f5마리§7의 §2무적 좀비§7 무리를 소환합니다. (지속: §f12초§7)",
        "§7좀비는 주변 §f8칸§7 이내의 적을 자동으로 추격합니다.",
        "",
        "§7좀비에게 공격당한 적은 §5감염§7 상태가 되어",
        "§8약화§7 효과를 받습니다.",
        "",
        "§7자신이 공격받으면 좀비들의 타겟이",
        "§7공격자로 변경됩니다."
}, summarize = {
        "§7패시브§f: 좀비가 공격 안 함",
        "§7철괴 우클릭§f: 좀비 5마리 소환"
})
public class Zombie extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 50;
    private static final int DURATION_SECONDS = 12;
    private static final int COUNT = 5;
    private static final double RANGE = 8.0;
    private static final String KEY_MINION = "zombie_minion";

    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);
    private int remainingSummonSeconds = 0;
    private final List<org.bukkit.entity.Zombie> minions = new ArrayList<>();

    public Zombie(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        registerTick();
        subscribeEvent(EntityTargetLivingEntityEvent.class);
        subscribeEvent(EntityDamageByEntityEvent.class);
    }

    @Override
    protected void onDeactivate() {
        unregisterTick();
        clearMinions();
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
        summonZombies();
        startSummon();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityTargetLivingEntityEvent) {
            onTarget((EntityTargetLivingEntityEvent) event);
        } else if (event instanceof EntityDamageByEntityEvent) {
            onDamageByEntity((EntityDamageByEntityEvent) event);
        }
    }

    private void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Zombie zombie)) {
            return;
        }
        if (event.getTarget() == null) {
            return;
        }
        if (event.getTarget().equals(getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (isMinion(zombie)) {
            if (event.getTarget() instanceof LivingEntity living
                    && !com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), living)) {
                event.setCancelled(true);
            }
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity().equals(getPlayer()) && event.getDamager() instanceof LivingEntity damager) {
            if (com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), damager)) {
                retargetMinions(damager);
            }
            return;
        }
        if (event.getDamager() instanceof org.bukkit.entity.Zombie zombie && isMinion(zombie)
                && event.getEntity() instanceof LivingEntity target) {
            Infection.apply(target, 20);
            event.setDamage(event.getDamage() * 0.5);
        }
    }

    @Override
    protected void onDestroy() {
        clearMinions();
    }

    private void summonZombies() {
        clearMinions();
        World world = getPlayer().getWorld();
        for (int i = 0; i < COUNT; i++) {
            org.bukkit.entity.Zombie zombie = world.spawn(getPlayer().getLocation(), org.bukkit.entity.Zombie.class);
            zombie.customName(Component.text("좀비 무리", NamedTextColor.DARK_GREEN));
            zombie.setCustomNameVisible(false);
            zombie.setInvulnerable(true);
            applySeasonalSkin(zombie);
            zombie.getPersistentDataContainer().set(new NamespacedKey(AbilityCombat.getPlugin(), KEY_MINION),
                    PersistentDataType.BYTE, (byte) 1);
            minions.add(zombie);
        }
        LivingEntity target = getNearestTarget();
        if (target != null) {
            retargetMinions(target);
        }
    }

    private LivingEntity getNearestTarget() {
        Player player = getPlayer();
        LivingEntity nearest = null;
        double min = Double.MAX_VALUE;
        for (LivingEntity entity : player.getWorld().getLivingEntities()) {
            if (entity.equals(player) || !com.abilitycombat.utils.LocationUtil.isValidTarget(getPlayer(), entity)) {
                continue;
            }
            double dist = entity.getLocation().distanceSquared(player.getLocation());
            if (dist <= RANGE * RANGE && dist < min) {
                min = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    private void retargetMinions(LivingEntity target) {
        for (org.bukkit.entity.Zombie zombie : minions) {
            if (zombie.isDead()) {
                continue;
            }
            zombie.setTarget(target);
        }
    }

    private void clearMinions() {
        for (org.bukkit.entity.Zombie zombie : minions) {
            if (!zombie.isDead()) {
                zombie.remove();
            }
        }
        minions.clear();
    }

    private void applySeasonalSkin(org.bukkit.entity.Zombie zombie) {
        if (zombie == null || zombie.getEquipment() == null) {
            return;
        }
        if (!isChristmas()) {
            return;
        }
        zombie.getEquipment().setHelmet(new ItemStack(Material.RED_WOOL));
    }

    private boolean isChristmas() {
        LocalDate date = LocalDate.now();
        return date.getMonthValue() == 12;
    }

    private boolean isMinion(org.bukkit.entity.Zombie zombie) {
        return zombie.getPersistentDataContainer().has(new NamespacedKey(AbilityCombat.getPlugin(), KEY_MINION),
                PersistentDataType.BYTE);
    }

    private void startSummon() {
        remainingSummonSeconds = DURATION_SECONDS;
        registerTick();
    }

    private void stopSummon() {
        clearMinions();
        remainingSummonSeconds = 0;
    }

    private boolean isSummoned() {
        return remainingSummonSeconds > 0;
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            if (isSummoned()) {
                remainingSummonSeconds--;
                if (remainingSummonSeconds <= 0) {
                    stopSummon();
                }
            }
        }
    }
}
