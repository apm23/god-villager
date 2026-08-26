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

/** Lightweight God Skeleton Horse runtime. */
public final class GodHorseRuntime {
    private static final String TAG = "godvillagers_god_horse";
    private static final String INIT_TAG = "godvillagers_god_horse_initialized_clean";
    private static final Set<SkeletonHorse> HORSES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final int FALL_SCAN_DEPTH = 24;
    private static final int SURFACE_SCAN_UP = 64;
    private static final int DISCOVERY_INTERVAL_TICKS = 100; // rare safety net; normal tracking is event-driven
    private static int discoveryTicker;

    private GodHorseRuntime() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(GodHorseRuntime::onLoad);
        ServerEntityEvents.ENTITY_UNLOAD.register(GodHorseRuntime::onUnload);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            HORSES.removeIf(horse -> horse.isRemoved() || !isRecognizedGodHorse(horse));
            for (SkeletonHorse horse : HORSES) tickHorse(horse);

            // Safety net for legacy worlds / entities that were loaded before their old marker
            // became visible. Runs only once every 5 seconds, never every tick.
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
                if (entity instanceof SkeletonHorse horse && !HORSES.contains(horse) && isRecognizedGodHorse(horse)) {
                    promote(horse);
                }
            }
        }
    }

    private static void promote(SkeletonHorse horse) {
        horse.addTag(TAG); // canonicalize old horses so future loads take the fast path
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

    /**
     * Legacy migration signature. Natural skeleton horses do not have these extreme
     * base attributes. Requiring several values together avoids converting normal horses.
     */
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

    private static boolean fluid(FluidState state) {
        return state.is(FluidTags.WATER) || state.is(FluidTags.LAVA);
    }

    private static boolean fluidAt(SkeletonHorse horse, int x, int y, int z) {
        return fluid(horse.level().getFluidState(new BlockPos(x, y, z)));
    }

    private static double topOfFluidColumn(SkeletonHorse horse, int x, int fluidY, int z) {
        int top = fluidY;
        for (int i = 0; i < SURFACE_SCAN_UP; i++) {
            if (!fluidAt(horse, x, top + 1, z)) return top + 1.0D;
            top++;
        }
        return top + 1.0D;
    }

    private static double surfaceY(SkeletonHorse horse) {
        int x = (int) Math.floor(horse.getX());
        int z = (int) Math.floor(horse.getZ());
        int y = (int) Math.floor(horse.getY());

        for (int dy = 1; dy >= -2; dy--) {
            int py = y + dy;
            if (fluidAt(horse, x, py, z)) return topOfFluidColumn(horse, x, py, z);
        }

        if (horse.getDeltaMovement().y > 0.0D) return Double.NaN;

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

        if (velocity.y > 0.05D && y >= surface - 0.05D) {
            unlockFluid(horse);
            return;
        }

        if (y > surface + 0.45D && velocity.y <= 0.0D) {
            unlockFluid(horse);
            return;
        }

        lockToSurface(horse, surface);
    }
}
