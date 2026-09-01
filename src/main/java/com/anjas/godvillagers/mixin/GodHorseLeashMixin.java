package com.anjas.godvillagers.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Alpha.83: allow only God Skeleton Horses to use vanilla lead mechanics.
 *
 * This deliberately hooks the generic leash eligibility check instead of any
 * movement/teleport code, so the proven alpha.81 surface-lock implementation
 * remains completely untouched. Vanilla lead attachment and fence-knot logic
 * continue to handle the actual leash once this eligibility check returns true.
 */
@Mixin(Mob.class)
public abstract class GodHorseLeashMixin {
    private static final String GOD_HORSE_TAG = "godvillagers_god_horse";

    @Inject(method = "canBeLeashed", at = @At("HEAD"), cancellable = true)
    private void godvillagers$allowGodHorseLeash(CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (self instanceof SkeletonHorse horse && horse.entityTags().contains(GOD_HORSE_TAG)) {
            cir.setReturnValue(true);
        }
    }
}
