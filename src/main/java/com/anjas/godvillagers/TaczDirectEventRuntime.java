package com.anjas.godvillagers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;

/**
 * Direct bridge for TACZ's own gun events. This intentionally uses reflection so
 * God Villagers keeps no hard runtime dependency when TACZ is absent.
 */
public final class TaczDirectEventRuntime {
    private static final double MAGNET_RADIUS = 3.0D;
    private static final int FRESH_ENTITY_TICKS = 8;

    private TaczDirectEventRuntime() {}

    public static void onGunHurtPost(Object event) {
        ServerPlayer shooter = serverPlayer(invoke(event, "getAttacker"));
        if (shooter == null) return;
        ItemStack gun = shooter.getMainHandItem();
        int level = TaczEnchantRuntime.enchantLevel(gun, TaczEnchantRuntime.LIFE_STEAL_ID);
        if (level <= 0) return;
        float damage = number(invoke(event, "getAmount"));
        if (damage <= 0.0F) {
            damage = number(invoke(event, "getBaseAmount"));
            float headshot = number(invoke(event, "getHeadshotMultiplier"));
            if (headshot > 0.0F) damage *= headshot;
        }
        if (damage > 0.0F) TaczEnchantRuntime.applyLifeSteal(shooter, damage, level);
    }

    public static void onGunKill(Object event) {
        ServerPlayer shooter = serverPlayer(invoke(event, "getAttacker"));
        Object killed = invoke(event, "getKilledEntity");
        if (shooter == null || !(killed instanceof LivingEntity victim)) return;

        ItemStack gun = shooter.getMainHandItem();
        int lifeSteal = TaczEnchantRuntime.enchantLevel(gun, TaczEnchantRuntime.LIFE_STEAL_ID);
        if (lifeSteal > 0) {
            float damage = number(invoke(event, "getBaseDamage"));
            float headshot = number(invoke(event, "getHeadshotMultiplier"));
            if (headshot > 0.0F) damage *= headshot;
            if (damage > 0.0F) TaczEnchantRuntime.applyLifeSteal(shooter, damage, lifeSteal);
        }

        if (TaczEnchantRuntime.sharedBossReward(victim)) return;
        if (TaczEnchantRuntime.enchantLevel(gun, TaczEnchantRuntime.MAGNET_ID) <= 0) return;
        if (!(victim.level() instanceof ServerLevel level) || shooter.level() != level) return;

        final double x = victim.getX(), y = victim.getY(), z = victim.getZ();
        // Queue after TACZ's kill event returns so vanilla/TACZ death rewards have had a
        // chance to spawn. The scan is tiny and event-only, independent of sniper range.
        level.getServer().execute(() -> deliverFreshRewards(level, shooter, x, y, z));
    }

    private static void deliverFreshRewards(ServerLevel level, ServerPlayer shooter, double x, double y, double z) {
        if (shooter.level() != level) return;
        AABB box = new AABB(x - MAGNET_RADIUS, y - MAGNET_RADIUS, z - MAGNET_RADIUS,
                x + MAGNET_RADIUS, y + MAGNET_RADIUS, z + MAGNET_RADIUS);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (entity.tickCount > FRESH_ENTITY_TICKS) continue;
            ItemStack remainder = entity.getItem().copy();
            if (remainder.isEmpty()) { entity.discard(); continue; }
            shooter.getInventory().add(remainder);
            if (remainder.isEmpty()) entity.discard();
            else {
                entity.setItem(remainder);
                entity.teleportTo(shooter.getX(), shooter.getY(), shooter.getZ());
                entity.setPickUpDelay(0);
            }
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
            if (orb.tickCount > FRESH_ENTITY_TICKS) continue;
            shooter.giveExperiencePoints(orb.getValue());
            orb.discard();
        }
    }

    private static ServerPlayer serverPlayer(Object value) {
        return value instanceof ServerPlayer player ? player : null;
    }

    private static float number(Object value) {
        return value instanceof Number n ? n.floatValue() : 0.0F;
    }

    private static Object invoke(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
