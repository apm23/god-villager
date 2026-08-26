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
 * Alpha.76 fixes fluid support so the horse does not sink when dismounted,
 * while preserving normal jump arcs before landing back on a fluid surface.
 */
public final class GodHorseRuntime {
    private static final String TAG = "godvillagers_god_horse";
    private static final String INIT_TAG = "godvillagers_god_horse_initialized_clean";
    private static final Set<SkeletonHorse> HORSES = Collections.newSetFromMap(new WeakHashMap<>());

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

    /**
     * Finds the top of the connected fluid column near the horse's feet.
     * It deliberately does not search far upward/downward, so a horse that is
     * genuinely airborne is not magnetically snapped to water below it.
     */
    private static double surfaceY(SkeletonHorse horse) {
        int x = (int) Math.floor(horse.getX());
        int z = (int) Math.floor(horse.getZ());
        int y = (int) Math.floor(horse.getY());
        int fluidY = Integer.MIN_VALUE;

        for (int dy = 1; dy >= -3; dy--) {
            int py = y + dy;
            if (fluid(horse.level().getFluidState(new BlockPos(x, py, z)))) {
                fluidY = py;
                break;
            }
        }
        if (fluidY == Integer.MIN_VALUE) return Double.NaN;

        int top = fluidY;
        for (int i = 0; i < 24; i++, top++) {
            if (!fluid(horse.level().getFluidState(new BlockPos(x, top, z)))) return (double) top;
        }
        return Double.NaN;
    }

    private static void unlockFluid(SkeletonHorse horse) {
        horse.setNoGravity(false);
        base(horse, Attributes.MOVEMENT_SPEED, 0.45D);
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
        double distanceToSurface = horse.getY() - surface;

        // Preserve a real jump: while rising, never snap the horse back down.
        // Once descending/touching the fluid, lock it cleanly to the surface.
        if (velocity.y > 0.05D || distanceToSurface > 0.70D) {
            unlockFluid(horse);
            return;
        }

        horse.setPos(horse.getX(), surface, horse.getZ());
        horse.setDeltaMovement(velocity.x, 0.0D, velocity.z);
        horse.setNoGravity(true);
        horse.setOnGround(true);

        // Fluid speed boost remains rider-only. An unattended horse stays stable
        // on the surface but keeps its normal land movement speed.
        base(horse, Attributes.MOVEMENT_SPEED, horse.isVehicle() ? 1.80D : 0.45D);
    }
}
