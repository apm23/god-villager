package com.anjas.godvillagers;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
 * Alpha.80: alpha.73 fluid-floor behavior, implemented directly in Java.
 *
 * The proven alpha.73 contract is preserved:
 * - if feet are in water/lava, climb upward until they are out of the fluid;
 * - recover from the solid-pool-floor/head-in-fluid edge cases;
 * - when feet are in air and the block exactly below is water/lava, hard-lock
 *   vertical motion to zero and treat the surface as ground;
 * - restore gravity only after leaving both the fluid body and its surface.
 *
 * Unlike alpha.73, this does not execute ~100 commands every server tick.
 */
public final class GodHorseRuntime {
    private static final String TAG = "godvillagers_god_horse";
    private static final String INIT_TAG = "godvillagers_god_horse_initialized_clean";
    private static final Set<SkeletonHorse> HORSES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final int MAX_RECOVERY_STEPS = 16; // exact alpha.73 recovery bound
    private static final int DISCOVERY_INTERVAL_TICKS = 100;
    private static int discoveryTicker;

    private GodHorseRuntime() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(GodHorseRuntime::onLoad);
        ServerEntityEvents.ENTITY_UNLOAD.register(GodHorseRuntime::onUnload);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            HORSES.removeIf(horse -> horse.isRemoved() || !isRecognizedGodHorse(horse));
            for (SkeletonHorse horse : HORSES) tickHorse(horse);

            if (++discoveryTicker >= DISCOVERY_INTERVAL_TICKS) {
                discoveryTicker = 0;
                discoverLegacyHorses(server);
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(GodHorseRuntime::allowDamage);
    }

    private static void onLoad(Entity entity, ServerLevel level) {
        if (entity instanceof SkeletonHorse horse && isRecognizedGodHorse(horse)) promote(horse);
    }

    private static void onUnload(Entity entity, ServerLevel level) {
        if (entity instanceof SkeletonHorse horse) HORSES.remove(horse);
    }

    private static void discoverLegacyHorses(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof SkeletonHorse horse
                        && !HORSES.contains(horse)
                        && isRecognizedGodHorse(horse)) {
                    promote(horse);
                }
            }
        }
    }

    private static void promote(SkeletonHorse horse) {
        horse.addTag(TAG);
        HORSES.add(horse);
        initialize(horse);
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof SkeletonHorse horse) || !isRecognizedGodHorse(horse)) return true;
        return !(source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_EXPLOSION));
    }

    private static boolean isRecognizedGodHorse(SkeletonHorse horse) {
        return horse.entityTags().contains(TAG)
                || horse.entityTags().contains(INIT_TAG)
                || hasGodHorseSignature(horse);
    }

    private static boolean hasGodHorseSignature(SkeletonHorse horse) {
        return approximately(baseValue(horse, Attributes.MAX_HEALTH), 80.0D, 0.01D)
                && approximately(baseValue(horse, Attributes.JUMP_STRENGTH), 1.8D, 0.01D)
                && (approximately(baseValue(horse, Attributes.MOVEMENT_SPEED), 0.45D, 0.01D)
                    || approximately(baseValue(horse, Attributes.MOVEMENT_SPEED), 1.80D, 0.01D));
    }

    private static double baseValue(SkeletonHorse horse, Holder<Attribute> attribute) {
        AttributeInstance instance = horse.getAttribute(attribute);
        return instance == null ? Double.NaN : instance.getBaseValue();
    }

    private static boolean approximately(double actual, double expected, double epsilon) {
        return !Double.isNaN(actual) && Math.abs(actual - expected) <= epsilon;
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

    private static boolean waterAt(SkeletonHorse horse, int yOffset) {
        return fluidAt(horse, yOffset).is(FluidTags.WATER);
    }

    private static boolean lavaAt(SkeletonHorse horse, int yOffset) {
        return fluidAt(horse, yOffset).is(FluidTags.LAVA);
    }

    private static FluidState fluidAt(SkeletonHorse horse, int yOffset) {
        int x = (int) Math.floor(horse.getX());
        int y = (int) Math.floor(horse.getY()) + yOffset;
        int z = (int) Math.floor(horse.getZ());
        return horse.level().getFluidState(new BlockPos(x, y, z));
    }

    private static boolean anyFluidAt(SkeletonHorse horse, int yOffset) {
        FluidState state = fluidAt(horse, yOffset);
        return state.is(FluidTags.WATER) || state.is(FluidTags.LAVA);
    }

    private static void moveUpOne(SkeletonHorse horse) {
        horse.setPos(horse.getX(), horse.getY() + 1.0D, horse.getZ());
    }

    /**
     * Direct translation of alpha.73's 16-pass recovery sequence.
     * Each condition is reevaluated after a move, just like the old commands were.
     */
    private static void alpha73Recovery(SkeletonHorse horse) {
        for (int i = 0; i < MAX_RECOVERY_STEPS; i++) {
            if (waterAt(horse, 0)) moveUpOne(horse);
            if (lavaAt(horse, 0)) moveUpOne(horse);

            if (!waterAt(horse, 0) && waterAt(horse, 1) && !waterAt(horse, -1)) moveUpOne(horse);
            if (!lavaAt(horse, 0) && lavaAt(horse, 1) && !lavaAt(horse, -1)) moveUpOne(horse);

            if (!waterAt(horse, 0) && !waterAt(horse, 1) && waterAt(horse, 2) && !waterAt(horse, -1)) moveUpOne(horse);
            if (!lavaAt(horse, 0) && !lavaAt(horse, 1) && lavaAt(horse, 2) && !lavaAt(horse, -1)) moveUpOne(horse);
        }
    }

    private static void alpha73SurfaceLock(SkeletonHorse horse) {
        boolean feetInWater = waterAt(horse, 0);
        boolean feetInLava = lavaAt(horse, 0);
        boolean waterBelow = waterAt(horse, -1);
        boolean lavaBelow = lavaAt(horse, -1);
        boolean onFluidSurface = (!feetInWater && waterBelow) || (!feetInLava && lavaBelow);

        if (onFluidSurface) {
            Vec3 velocity = horse.getDeltaMovement();
            horse.setNoGravity(true);
            horse.setOnGround(true);
            horse.setDeltaMovement(velocity.x, 0.0D, velocity.z);
            base(horse, Attributes.MOVEMENT_SPEED, 1.80D);
            return;
        }

        base(horse, Attributes.MOVEMENT_SPEED, 0.45D);

        // Exact alpha.73 gravity restore condition: only away from both the fluid body
        // and the one-block-below surface.
        if (!waterBelow && !lavaBelow && !feetInWater && !feetInLava) {
            horse.setNoGravity(false);
        }
    }

    private static void tickHorse(SkeletonHorse horse) {
        if (!horse.entityTags().contains(INIT_TAG)) initialize(horse);
        horse.clearFire();

        alpha73Recovery(horse);
        alpha73SurfaceLock(horse);
    }
}
