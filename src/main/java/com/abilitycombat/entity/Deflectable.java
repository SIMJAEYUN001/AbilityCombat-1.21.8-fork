package com.abilitycombat.entity;

import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public interface Deflectable {

    boolean deflect(LivingEntity deflector, Vector newVelocity);
}
