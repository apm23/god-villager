package com.anjas.godvillagers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Runtime constants and balancing rules for the optional TACZ enchant integration.
 * No TACZ class is referenced directly, keeping God Villagers safe when TACZ is absent.
 */
public final class TaczEnchantRuntime {
    public static final String MAGNET_ID = "godvillagers:magnet";
    public static final String LIFE_STEAL_ID = "godvillagers:life_steal";
    public static final int MAX_LIFE_STEAL_LEVEL = 3;

    private static final float[] LIFE_STEAL_RATIOS = {0.0F, 0.05F, 0.075F, 0.10F};
    public static final float ABSORPTION_RATIO = 0.50F;
    public static final float MAX_ABSORPTION_HEALTH = 4.0F;

    private TaczEnchantRuntime() {}

    public static float healingForDamage(float actualBulletDamage, int level) {
        if (actualBulletDamage <= 0.0F || level <= 0) return 0.0F;
        return actualBulletDamage * LIFE_STEAL_RATIOS[Math.min(level, MAX_LIFE_STEAL_LEVEL)];
    }

    public static float absorptionForOverflow(float unusedHealing) {
        return Math.max(0.0F, unusedHealing) * ABSORPTION_RATIO;
    }

    public static void afterSuccessfulDamage(LivingEntity victim, DamageSource source, float requestedDamage) {
        if (victim == null || source == null || requestedDamage <= 0.0F) return;
        if (!looksLikeTaczBullet(source)) return;
        ServerPlayer shooter = exactShooter(source);
        if (shooter == null) return;
        // Never use nearest-player/global attacker fallback. Gun/enchantment resolution
        // must be tied to this exact shooter before Life Steal or Magnet can execute.
    }

    /** Only accepts the player explicitly owned by this damage source. */
    static ServerPlayer exactShooter(DamageSource source) {
        Entity owner = source.getEntity();
        return owner instanceof ServerPlayer player ? player : null;
    }

    static boolean looksLikeTaczBullet(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct == null) return false;
        String name = direct.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("tacz") && (name.contains("bullet") || name.contains("projectile"));
    }

    /** Boss/shared rewards are intentionally excluded from future Magnet collection. */
    static boolean sharedBossReward(LivingEntity victim) {
        String id = victim.getType().toString().toLowerCase(java.util.Locale.ROOT);
        return id.contains("ender_dragon") || id.contains("wither");
    }
}
