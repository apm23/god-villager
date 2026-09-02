package com.anjas.godvillagers.mixin;

import com.anjas.godvillagers.TaczDirectEventRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Direct optional hook into TACZ's gun-kill event. */
@Mixin(targets = "com.tacz.guns.api.event.common.EntityKillByGunEvent", remap = false)
public abstract class TaczKillEventMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void godvillagers$afterTaczGunKill(CallbackInfo ci) {
        TaczDirectEventRuntime.onGunKill(this);
    }
}
