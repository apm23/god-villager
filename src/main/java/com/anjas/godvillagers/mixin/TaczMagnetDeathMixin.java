package com.anjas.godvillagers.mixin;

import com.anjas.godvillagers.TaczEnchantRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Death-side bridge for strict TACZ Magnet ownership.
 * This stage only resolves and consumes the exact fatal shooter. Actual reward
 * relocation is added after this lifecycle hook is verified against MC 26.2.
 */
@Mixin(LivingEntity.class)
public abstract class TaczMagnetDeathMixin {
    @Inject(method = "die", at = @At("HEAD"), require = 0)
    private void godvillagers$resolveMagnetOwner(DamageSource source, CallbackInfo ci) {
        LivingEntity victim = (LivingEntity)(Object)this;
        UUID shooterId = TaczEnchantRuntime.consumeMagnetFatalShooter(victim);
        if (shooterId == null) return;
        if (!(victim.level() instanceof ServerLevel level)) return;
        ServerPlayer shooter = level.getServer().getPlayerList().getPlayer(shooterId);
        if (shooter == null) return;
        TaczEnchantRuntime.markPendingMagnetReward(victim, shooter);
    }
}
