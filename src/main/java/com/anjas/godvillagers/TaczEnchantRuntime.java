package com.anjas.godvillagers;

/**
 * Runtime constants and balancing rules for the optional TACZ enchant integration.
 *
 * This class deliberately has no compile-time references to TACZ.  The final
 * event bridge may only be installed when Fabric Loader reports that TACZ is
 * present, so God Villagers remains safe on servers/worlds without TACZ.
 */
public final class TaczEnchantRuntime {
    public static final String MAGNET_ID = "godvillagers:magnet";
    public static final String LIFE_STEAL_ID = "godvillagers:life_steal";
    public static final int MAX_LIFE_STEAL_LEVEL = 3;

    // Conservative scaling: 5/7.5/10% of actual bullet damage becomes red-health healing.
    private static final float[] LIFE_STEAL_RATIOS = {0.0F, 0.05F, 0.075F, 0.10F};
    // Yellow-heart overflow is deliberately half as efficient as normal healing.
    public static final float ABSORPTION_RATIO = 0.50F;
    // Prevent sustained automatic fire from building an effectively immortal buffer.
    public static final float MAX_ABSORPTION_HEALTH = 4.0F; // 2 hearts

    private TaczEnchantRuntime() {}

    public static float healingForDamage(float actualBulletDamage, int level) {
        if (actualBulletDamage <= 0.0F || level <= 0) return 0.0F;
        int safeLevel = Math.min(level, MAX_LIFE_STEAL_LEVEL);
        return actualBulletDamage * LIFE_STEAL_RATIOS[safeLevel];
    }

    public static float absorptionForOverflow(float unusedHealing) {
        return Math.max(0.0F, unusedHealing) * ABSORPTION_RATIO;
    }
}
