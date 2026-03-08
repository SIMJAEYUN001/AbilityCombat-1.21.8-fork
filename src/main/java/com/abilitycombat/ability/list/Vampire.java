package com.abilitycombat.ability.list;

import com.abilitycombat.ability.AbilityBase;
import com.abilitycombat.ability.AbilityManifest;
import com.abilitycombat.ability.handler.ActiveHandler;
import com.abilitycombat.game.Participant;
import com.abilitycombat.utils.ParticleUtil;
import com.abilitycombat.utils.LocationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;

@AbilityManifest(name = "뱀파이어 (Vampire)", rank = AbilityManifest.Rank.A, species = AbilityManifest.Species.UNDEAD, explain = {
        "§e§l[철괴 우클릭 - 피의 제전]§f §8(쿨타임: 60초)",
        "§7주변 §f8칸§7 내의 모든 플레이어에게 §c3의 고정 피해§7를 입히고",
        "§7'피의 힘'을 §f1스택§7 획득합니다.",
        "",
        "§e§l[패시브 - 흡혈의 정석]",
        "§7피의 힘을 1스택 획득할 때마다 §a3의 체력§7을 회복하고",
        "§7최대 체력이 §c1§7만큼 영구적으로 증가합니다.",
        "§7피의 힘 스택당 §f+0.4§7의 추가 피해를 입힙니다."
    }, summarize = {
        "§7철괴 우클릭§f: 광역 고정 피해 및 스택 획득",
        "§7패시브§f: 스택당 체력/최대체력 증가, 스택당 추가 데미지"
    })
public class Vampire extends AbilityBase implements ActiveHandler {

    private static final int COOLDOWN_SECONDS = 60;
    private static final double ACTIVE_RANGE = 8.0;
    private static final double ACTIVE_DAMAGE = 3.0;
    private static final double HEAL_PER_STACK = 3.0;
    private static final double MAX_HP_PER_STACK = 1.0;
    private static final double NIGHT_DAMAGE_MULTIPLIER = 0.4;
    private static final int MAX_STACKS = 4;

    private int bloodPowerStacks = 0;
    private final Cooldown cooldown = new Cooldown(COOLDOWN_SECONDS);

    public Vampire(Participant participant) {
        super(participant);
    }

    @Override
    protected void onActivate() {
        subscribeEvent(EntityDamageByEntityEvent.class);
        updateStackDisplay();
    }

    @Override
    public void handleBridgeEvent(Event event) {
        if (event instanceof EntityDamageByEntityEvent e) {
            onDamageByEntity(e);
        }
    }

    private void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!event.getDamager().equals(getPlayer())) {
            return;
        }
        if (bloodPowerStacks > 0) {
            double bonus = bloodPowerStacks * NIGHT_DAMAGE_MULTIPLIER;
            event.setDamage(event.getDamage() + bonus);
        }
    }

    @Override
    protected void onDeactivate() {
        AttributeInstance maxHealth = getPlayer().getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0);
        }
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

        List<Player> targets = LocationUtil.getNearbyLivingEntities(getPlayer().getLocation(), ACTIVE_RANGE, getPlayer(),
                e -> e instanceof Player && !e.equals(getPlayer()))
                .stream().map(e -> (Player) e).toList();

        if (targets.isEmpty()) {
            getPlayer().sendMessage("§c주변에 플레이어가 없습니다.");
            return false;
        }

        for (Player target : targets) {
            target.damage(ACTIVE_DAMAGE, getPlayer());
            ParticleUtil.spawnParticle(target.getWorld(), Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0),
                    5, 0.2, 0.2, 0.2, 0.1);
        }

        getPlayer().getWorld().playSound(getPlayer().getLocation(), Sound.ENTITY_VEX_CHARGE, 1.0f, 0.5f);

        int hitCount = targets.size();
        int gainedStacks = Math.min(MAX_STACKS - bloodPowerStacks, hitCount);
        if (gainedStacks > 0) {
            bloodPowerStacks += gainedStacks;
            heal(HEAL_PER_STACK * gainedStacks);
            addMaxHealth(MAX_HP_PER_STACK * gainedStacks);
        }

        updateStackDisplay();
        cooldown.start();
        applyIronCooldownIfEmpty(COOLDOWN_SECONDS);
        return true;
    }

    private void heal(double amount) {
        double maxHealth = getPlayer().getAttribute(Attribute.MAX_HEALTH).getValue();
        getPlayer().setHealth(Math.min(maxHealth, getPlayer().getHealth() + amount));
    }

    private void addMaxHealth(double amount) {
        AttributeInstance maxHealth = getPlayer().getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() + amount);
        }
    }

    private void updateStackDisplay() {
        getActionbarChannel().update(getPlayer(), "vampire:stacks", 10,
                Component.text("§c피의 힘: §f" + bloodPowerStacks + " 스택"));
    }

    @Override
    public void onTick(int tick) {
        if (tick % 20 == 0) {
            updateStackDisplay();
        }
    }
}
