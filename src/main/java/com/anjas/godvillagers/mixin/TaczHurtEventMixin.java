package com.anjas.godvillagers.mixin;

import com.anjas.godvillagers.TaczDirectEventRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Direct optional hook into TACZ's server gun-hit event. */
@Mixin(targets = "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Post", remap = false)
public abstract class TaczHurtEventMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void godvillagers$afterTaczGunHurt(CallbackInfo ci) {
        TaczDirectEventRuntime.onGunHurtPost(this);
    }
}
