package com.anjas.godvillagers.mixin;

import com.anjas.godvillagers.TaczEnchantRuntime;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side vanilla damage bridge used by the optional TACZ enchant runtime.
 *
 * This mixin intentionally references only Minecraft classes. TACZ detection stays
 * inside TaczEnchantRuntime so servers without TACZ never have to resolve TACZ
 * classes during mixin application.
 */
@Mixin(LivingEntity.class)
public abstract class TaczBulletDamageMixin {
    @Inject(method = "hurtServer", at = @At("RETURN"), require = 0)
    private void godvillagers$afterServerDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        TaczEnchantRuntime.afterSuccessfulDamage((LivingEntity)(Object)this, source, amount);
    }
}
