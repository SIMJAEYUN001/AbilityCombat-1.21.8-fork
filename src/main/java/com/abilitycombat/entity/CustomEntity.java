package com.abilitycombat.entity;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.function.Predicate;

public abstract class CustomEntity {

    private final World world;
    private Location location;
    private Vector velocity = new Vector();
    private BoundingBox localBox = new BoundingBox(-0.25, -0.25, -0.25, 0.25, 0.25, 0.25);
    private double gravity = 0.03;
    private double drag = 0.0;
    private int age = 0;
    private int maxAge = 200;
    private boolean alive = true;
    private boolean spawned = false;
    private boolean collideBlocks = true;
    private boolean collideEntities = true;
    private double raySize = 0.25;
    private LivingEntity source;
    private Predicate<LivingEntity> entityPredicate;

    protected CustomEntity(World world, Location location) {
        this.world = Objects.requireNonNull(world, "world");
        this.location = location.clone();
    }

    protected CustomEntity(World world, double x, double y, double z) {
        this(world, new Location(world, x, y, z));
    }

    public void spawn() {
        if (spawned) {
            return;
        }
        spawned = true;
        CustomEntityManager.register(this);
        onSpawn();
    }

    public boolean isAlive() {
        return alive;
    }

    public void remove() {
        if (!alive) {
            return;
        }
        alive = false;
        onRemove();
        CustomEntityManager.unregister(this);
    }

    public Location getLocation() {
        return location.clone();
    }

    public void setLocation(Location location) {
        if (location != null) {
            this.location = location.clone();
        }
    }

    public World getWorld() {
        return world;
    }

    public Vector getVelocity() {
        return velocity.clone();
    }

    public void setVelocity(Vector velocity) {
        if (velocity != null) {
            this.velocity = velocity.clone();
        }
    }

    public void setSource(LivingEntity source) {
        this.source = source;
    }

    public LivingEntity getSource() {
        return source;
    }

    public void setGravity(double gravity) {
        this.gravity = gravity;
    }

    public void setDrag(double drag) {
        this.drag = Math.max(0.0, Math.min(1.0, drag));
    }

    public void setMaxAge(int ticks) {
        this.maxAge = ticks;
    }

    public void setCollideBlocks(boolean collideBlocks) {
        this.collideBlocks = collideBlocks;
    }

    public void setCollideEntities(boolean collideEntities) {
        this.collideEntities = collideEntities;
    }

    public void setEntityPredicate(Predicate<LivingEntity> predicate) {
        this.entityPredicate = predicate;
    }

    public BoundingBox getBoundingBox() {
        return localBox.shift(location.toVector());
    }

    public CustomEntity resizeBoundingBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        localBox = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        updateRaySize();
        return this;
    }

    public void setRaySize(double raySize) {
        this.raySize = Math.max(0.0, raySize);
    }

    protected boolean tick() {
        if (!alive) {
            return false;
        }
        if (maxAge > 0 && age++ >= maxAge) {
            remove();
            return false;
        }
        applyPhysics();
        moveWithCollision();
        onTick();
        return alive;
    }

    protected void onSpawn() {
    }

    protected void onTick() {
    }

    protected void onRemove() {
    }

    protected boolean onHitEntity(LivingEntity entity, Location hitLocation) {
        return true;
    }

    protected boolean onHitBlock(Block block, Location hitLocation) {
        return true;
    }

    private void applyPhysics() {
        if (gravity != 0.0) {
            velocity.setY(velocity.getY() - gravity);
        }
        if (drag > 0.0) {
            velocity.multiply(Math.max(0.0, 1.0 - drag));
        }
    }

    private void moveWithCollision() {
        if (velocity.lengthSquared() <= 0.0) {
            return;
        }
        Vector direction = velocity.clone().normalize();
        double maxDistance = velocity.length();

        RayTraceResult entityHit = null;
        if (collideEntities) {
            entityHit = world.rayTraceEntities(location, direction, maxDistance, raySize, this::shouldHitEntity);
        }
        RayTraceResult blockHit = null;
        if (collideBlocks) {
            blockHit = world.rayTraceBlocks(location, direction, maxDistance, FluidCollisionMode.NEVER, true);
        }

        double entityDistance = distanceToHit(entityHit);
        double blockDistance = distanceToHit(blockHit);
        if (entityHit != null && entityDistance <= blockDistance) {
            Location hitLocation = hitLocation(entityHit);
            if (hitLocation != null) {
                location = hitLocation;
            } else {
                location = location.clone().add(direction.clone().multiply(entityDistance));
            }
            Entity hitEntity = entityHit.getHitEntity();
            if (hitEntity instanceof LivingEntity living) {
                if (onHitEntity(living, location.clone())) {
                    remove();
                }
            }
            return;
        }
        if (blockHit != null) {
            Location hitLocation = hitLocation(blockHit);
            if (hitLocation != null) {
                location = hitLocation;
            } else {
                location = location.clone().add(direction.clone().multiply(blockDistance));
            }
            Block hitBlock = blockHit.getHitBlock();
            if (hitBlock != null) {
                if (onHitBlock(hitBlock, location.clone())) {
                    remove();
                }
            }
            return;
        }
        location = location.clone().add(velocity);
    }

    private double distanceToHit(RayTraceResult result) {
        if (result == null || result.getHitPosition() == null) {
            return Double.MAX_VALUE;
        }
        return result.getHitPosition().distance(location.toVector());
    }

    private Location hitLocation(RayTraceResult result) {
        if (result == null || result.getHitPosition() == null) {
            return null;
        }
        return result.getHitPosition().toLocation(world);
    }

    private boolean shouldHitEntity(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        if (source != null && source.getUniqueId().equals(entity.getUniqueId())) {
            return false;
        }
        return entityPredicate == null || entityPredicate.test(living);
    }

    private void updateRaySize() {
        double width = Math.max(localBox.getWidthX(), localBox.getWidthZ());
        raySize = Math.max(0.1, width * 0.5);
    }
}
