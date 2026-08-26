package com.anjas.godvillagers;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Lightweight event-driven runtime for tagged God Skeleton Horses.
 *
 * The old gameplay contract is preserved: water and lava act like a solid
 * floor for the God Horse, with or without a rider. Unlike the original heavy
 * implementation, this tracks only loaded tagged horses and normally samples
 * just the blocks around each horse's feet. A bounded downward scan is used
 * only while the horse is airborne/falling toward a fluid surface.
 */
public final class GodHorseRuntime {
    private static final String TAG = "godvillagers_god_horse";
    private static final String INIT_TAG = "godvillagers_god_horse_initialized_clean";
    private static final Set<SkeletonHorse> HORSES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final int FALL_SCAN_DEPTH = 24;
    private static final int SURFACE_SCAN_UP = 64;

    private GodHorseRuntime() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(GodHorseRuntime::onLoad);
        ServerEntityEvents.ENTITY_UNLOAD.register(GodHorseRuntime::onUnload);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            HORSES.removeIf(horse -> horse.isRemoved() || !isGodHorse(horse));
            for (SkeletonHorse horse : HORSES) tickHorse(horse);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(GodHorseRuntime::allowDamage);
    }

    private static void onLoad(Entity entity, net.minecraft.server.level.ServerLevel level) {
        if (entity instanceof SkeletonHorse horse && isGodHorse(horse)) {
            HORSES.add(horse);
            initialize(horse);
        }
    }

    private static void onUnload(Entity entity, net.minecraft.server.level.ServerLevel level) {
        if (entity instanceof SkeletonHorse horse) HORSES.remove(horse);
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof SkeletonHorse horse) || !isGodHorse(horse)) return true;
        if (source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        return true;
    }

    private static boolean isGodHorse(SkeletonHorse horse) {
        return horse.entityTags().contains(TAG);
    }

    private static void base(SkeletonHorse horse, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = horse.getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) instance.setBaseValue(value);
    }

    private static void initialize(SkeletonHorse horse) {
        base(horse, Attributes.MAX_HEALTH, 80.0D);
        base(horse, Attributes.MOVEMENT_SPEED, 0.45D);
        base(horse, Attributes.JUMP_STRENGTH, 1.8D);
        base(horse, Attributes.FALL_DAMAGE_MULTIPLIER, 0.0D);
        base(horse, Attributes.SAFE_FALL_DISTANCE, 1024.0D);
        base(horse, Attributes.MOVEMENT_EFFICIENCY, 1.0D);
        base(horse, Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D);
        base(horse, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0D);
        if (horse.getHealth() < 80.0F) horse.setHealth(80.0F);
        horse.addTag(INIT_TAG);
    }

    private static boolean fluid(FluidState state) {
        return state.is(FluidTags.WATER) || state.is(FluidTags.LAVA);
    }

    private static boolean fluidAt(SkeletonHorse horse, int x, int y, int z) {
        return fluid(horse.level().getFluidState(new BlockPos(x, y, z)));
    }

    /** Returns the top Y of a connected fluid column, starting from a known fluid block. */
    private static double topOfFluidColumn(SkeletonHorse horse, int x, int fluidY, int z) {
        int top = fluidY;
        for (int i = 0; i < SURFACE_SCAN_UP; i++) {
            if (!fluidAt(horse, x, top + 1, z)) return top + 1.0D;
            top++;
        }
        return top + 1.0D;
    }

    /**
     * Finds a fluid surface beneath/around the horse.
     * Fast path: the horse is already on/inside fluid, requiring only a few reads.
     * Slow path: only while falling, scan a bounded distance downward so a jump or
     * fall into water/lava cannot slip several blocks below the surface.
     */
    private static double surfaceY(SkeletonHorse horse) {
        int x = (int) Math.floor(horse.getX());
        int z = (int) Math.floor(horse.getZ());
        int y = (int) Math.floor(horse.getY());

        // Normal steady-state: surface directly under feet or horse currently in fluid.
        for (int dy = 1; dy >= -2; dy--) {
            int py = y + dy;
            if (fluidAt(horse, x, py, z)) return topOfFluidColumn(horse, x, py, z);
        }

        // While rising, do not magnetically snap to a fluid surface below.
        if (horse.getDeltaMovement().y > 0.0D) return Double.NaN;

        // Falling/entering fluid: bounded look-down prevents tunnelling through surface.
        for (int depth = 3; depth <= FALL_SCAN_DEPTH; depth++) {
            int py = y - depth;
            if (fluidAt(horse, x, py, z)) return topOfFluidColumn(horse, x, py, z);
        }
        return Double.NaN;
    }

    private static void unlockFluid(SkeletonHorse horse) {
        horse.setNoGravity(false);
        base(horse, Attributes.MOVEMENT_SPEED, 0.45D);
    }

    private static void lockToSurface(SkeletonHorse horse, double surface) {
        Vec3 velocity = horse.getDeltaMovement();
        horse.setPos(horse.getX(), surface, horse.getZ());
        horse.setDeltaMovement(velocity.x, 0.0D, velocity.z);
        horse.setNoGravity(true);
        horse.setOnGround(true);
        base(horse, Attributes.MOVEMENT_SPEED, horse.isVehicle() ? 1.80D : 0.45D);
    }

    private static void tickHorse(SkeletonHorse horse) {
        if (!horse.entityTags().contains(INIT_TAG)) initialize(horse);
        horse.clearFire();

        double surface = surfaceY(horse);
        if (Double.isNaN(surface)) {
            unlockFluid(horse);
            return;
        }

        Vec3 velocity = horse.getDeltaMovement();
        double y = horse.getY();

        // Preserve real jumps. As soon as the horse is rising away from the surface,
        // gravity is restored so the arc remains natural and it can land again.
        if (velocity.y > 0.05D && y >= surface - 0.05D) {
            unlockFluid(horse);
            return;
        }

        // Do not pull a horse downward from high in the air. Wait until it actually
        // reaches/crosses the virtual fluid floor. If vanilla moved it slightly below
        // the surface in one tick, snap it back immediately before it can sink farther.
        if (y > surface + 0.45D && velocity.y <= 0.0D) {
            unlockFluid(horse);
            return;
        }

        lockToSurface(horse, surface);
    }
}
