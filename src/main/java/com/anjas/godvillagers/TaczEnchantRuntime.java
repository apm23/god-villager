package com.anjas.godvillagers;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Optional TACZ enchant runtime with strict per-shooter ownership. */
public final class TaczEnchantRuntime {
    public static final String MAGNET_ID = "godvillagers:magnet";
    public static final String LIFE_STEAL_ID = "godvillagers:life_steal";
    public static final int MAX_LIFE_STEAL_LEVEL = 3;
    private static final float[] LIFE_STEAL_RATIOS = {0.0F, 0.05F, 0.075F, 0.10F};
    public static final float ABSORPTION_RATIO = 0.50F;
    public static final float MAX_ABSORPTION_HEALTH = 4.0F;
    private static final Map<UUID, UUID> MAGNET_FATAL_SHOOTERS = new ConcurrentHashMap<>();

    private TaczEnchantRuntime() {}

    public static float healingForDamage(float damage, int level) {
        if (damage <= 0.0F || level <= 0) return 0.0F;
        return damage * LIFE_STEAL_RATIOS[Math.min(level, MAX_LIFE_STEAL_LEVEL)];
    }

    public static float absorptionForOverflow(float unusedHealing) {
        return Math.max(0.0F, unusedHealing) * ABSORPTION_RATIO;
    }

    public static void afterSuccessfulDamage(LivingEntity victim, DamageSource source, float requestedDamage) {
        if (victim == null || source == null || requestedDamage <= 0.0F || !looksLikeTaczBullet(source)) return;
        ServerPlayer shooter = exactShooter(source);
        if (shooter == null) return;
        ItemStack gun = shooter.getMainHandItem();
        if (!looksLikeTaczGun(gun)) return;
        int lifeSteal = enchantLevel(gun, LIFE_STEAL_ID);
        int magnet = enchantLevel(gun, MAGNET_ID);
        if (lifeSteal <= 0 && magnet <= 0) return;
        if (lifeSteal > 0) applyLifeSteal(shooter, requestedDamage, lifeSteal);
        if (magnet > 0 && victim.isDeadOrDying() && !sharedBossReward(victim)) {
            MAGNET_FATAL_SHOOTERS.put(victim.getUUID(), shooter.getUUID());
        }
    }

    public static UUID consumeMagnetFatalShooter(LivingEntity victim) {
        if (victim == null || sharedBossReward(victim)) return null;
        return MAGNET_FATAL_SHOOTERS.remove(victim.getUUID());
    }

    static void applyLifeSteal(ServerPlayer shooter, float bulletDamage, int level) {
        float budget = healingForDamage(bulletDamage, level);
        if (budget <= 0.0F) return;
        float missing = Math.max(0.0F, shooter.getMaxHealth() - shooter.getHealth());
        float directHeal = Math.min(missing, budget);
        if (directHeal > 0.0F) shooter.heal(directHeal);
        float overflow = budget - directHeal;
        if (overflow <= 0.0F) return;
        shooter.setAbsorptionAmount(Math.min(MAX_ABSORPTION_HEALTH, shooter.getAbsorptionAmount() + absorptionForOverflow(overflow)));
    }

    static ServerPlayer exactShooter(DamageSource source) {
        Entity owner = source.getEntity();
        return owner instanceof ServerPlayer player ? player : null;
    }

    static boolean looksLikeTaczBullet(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct == null) return false;
        String name = direct.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("tacz") && (name.contains("bullet") || name.contains("projectile"));
    }

    static boolean looksLikeTaczGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        return id.startsWith("tacz:") || id.contains(":tacz_") || id.contains("tacz");
    }

    static int enchantLevel(ItemStack stack, String wantedId) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            var key = holder.unwrapKey();
            if (key.isPresent() && key.get().identifier().toString().equals(wantedId)) return enchantments.getLevel(holder);
        }
        return 0;
    }

    static boolean sharedBossReward(LivingEntity victim) {
        String id = victim.getType().toString().toLowerCase(Locale.ROOT);
        return id.contains("ender_dragon") || id.contains("wither");
    }
}
