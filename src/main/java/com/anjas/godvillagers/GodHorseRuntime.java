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
 * Alpha.81: alpha.73 fluid-floor behavior using synchronized server teleports.
 *
 * Alpha.80 reproduced the old block predicates but used Entity#setPos for the
 * one-block recovery. That is sufficient for an empty horse, but a player-controlled
 * vehicle can immediately resync its ridden position and undo that raw position write.
 * Alpha.73 used /tp, whose entity teleport semantics synchronize the vehicle position.
 * This version keeps the lightweight direct block checks but uses Entity#teleportTo for
 * recovery moves so mounted horses and their passengers receive a real server teleport.
 */
public final class GodHorseRuntime {
    private static final String TAG = "godvillagers_god_horse";
    private static final String INIT_TAG = "godvillagers_god_horse_initialized_clean";
    private static final Set<SkeletonHorse> HORSES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final int MAX_RECOVERY_STEPS = 16;
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

    /**
     * Same-world synchronized teleport equivalent to alpha.73's "tp @s ~ ~1 ~".
     * This is intentionally NOT setPos: ridden entities need the server teleport path
     * so the vehicle/passenger tracking state is updated instead of being overwritten
     * by the next controlling-passenger movement update.
     */
    private static boolean teleportUpOne(SkeletonHorse horse) {
        if (!(horse.level() instanceof ServerLevel level)) return false;
        Vec3 velocity = horse.getDeltaMovement();
        boolean moved = horse.teleportTo(
                level,
                horse.getX(), horse.getY() + 1.0D, horse.getZ(),
                Set.of(), horse.getYRot(), horse.getXRot(), false);
        if (moved) {
            horse.setDeltaMovement(velocity.x, 0.0D, velocity.z);
            horse.resetFallDistance();
        }
        return moved;
    }

    /** Direct translation of alpha.73's 16-pass recovery sequence. */
    private static void alpha73Recovery(SkeletonHorse horse) {
        for (int i = 0; i < MAX_RECOVERY_STEPS; i++) {
            boolean moved = false;

            if (waterAt(horse, 0)) moved |= teleportUpOne(horse);
            if (lavaAt(horse, 0)) moved |= teleportUpOne(horse);

            if (!waterAt(horse, 0) && waterAt(horse, 1) && !waterAt(horse, -1)) moved |= teleportUpOne(horse);
            if (!lavaAt(horse, 0) && lavaAt(horse, 1) && !lavaAt(horse, -1)) moved |= teleportUpOne(horse);

            if (!waterAt(horse, 0) && !waterAt(horse, 1) && waterAt(horse, 2) && !waterAt(horse, -1)) moved |= teleportUpOne(horse);
            if (!lavaAt(horse, 0) && !lavaAt(horse, 1) && lavaAt(horse, 2) && !lavaAt(horse, -1)) moved |= teleportUpOne(horse);

            // Unlike the old command loop, stop immediately when no rule moved the horse.
            // This preserves the result while eliminating the remaining useless passes.
            if (!moved) break;
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
            horse.resetFallDistance();
            base(horse, Attributes.MOVEMENT_SPEED, 1.80D);
            return;
        }

        base(horse, Attributes.MOVEMENT_SPEED, 0.45D);
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
