package com.anjas.godvillagers;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public final class GodHorseRuntime {
    private static final String TAG = "godvillagers_god_horse";
    private static final String INIT_TAG = "godvillagers_god_horse_initialized_clean";
    private static final Set<SkeletonHorse> HORSES = Collections.newSetFromMap(new WeakHashMap<>());

    private GodHorseRuntime() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof SkeletonHorse horse && isGodHorse(horse)) {
                HORSES.add(horse);
                initialize(horse);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof SkeletonHorse horse) HORSES.remove(horse);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            HORSES.removeIf(horse -> horse.isRemoved() || !isGodHorse(horse));
            for (SkeletonHorse horse : HORSES) tickHorse(horse);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof SkeletonHorse horse) || !isGodHorse(horse)) return true;
            if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) return false;
            return true;
        });
    }

    private static boolean isGodHorse(SkeletonHorse horse) {
        return horse.entityTags().contains(TAG);
    }

    private static void base(SkeletonHorse horse, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
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

    private static double surfaceY(SkeletonHorse horse) {
        int x = (int)Math.floor(horse.getX());
        int z = (int)Math.floor(horse.getZ());
        int y = (int)Math.floor(horse.getY());

        int fluidY = Integer.MIN_VALUE;
        for (int dy = 2; dy >= -2; dy--) {
            int py = y + dy;
            if (fluid(horse.level().getFluidState(new BlockPos(x, py, z)))) {
                fluidY = py;
                break;
            }
        }
        if (fluidY == Integer.MIN_VALUE) return Double.NaN;

        int top = fluidY;
        for (int i = 0; i < 24; i++, top++) {
            if (!fluid(horse.level().getFluidState(new BlockPos(x, top, z)))) return top;
        }
        return Double.NaN;
    }

    private static void tickHorse(SkeletonHorse horse) {
        if (!horse.entityTags().contains(INIT_TAG)) initialize(horse);
        horse.clearFire();

        if (!horse.isVehicle()) {
            horse.setNoGravity(false);
            base(horse, Attributes.MOVEMENT_SPEED, 0.45D);
            return;
        }

        double surface = surfaceY(horse);
        if (Double.isNaN(surface)) {
            horse.setNoGravity(false);
            base(horse, Attributes.MOVEMENT_SPEED, 0.45D);
            return;
        }

        Vec3 motion = horse.getDeltaMovement();
        horse.setPos(horse.getX(), surface, horse.getZ());
        horse.setDeltaMovement(motion.x, 0.0D, motion.z);
        horse.setNoGravity(true);
        horse.setOnGround(true);
        base(horse, Attributes.MOVEMENT_SPEED, 1.80D);
    }
}
