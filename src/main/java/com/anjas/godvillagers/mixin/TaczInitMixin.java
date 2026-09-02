package com.anjas.godvillagers.mixin;

import com.anjas.godvillagers.TaczDirectEventRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers the optional God Villagers bridge after TACZ has initialized its Fabric events. */
@Mixin(targets = "cn.sh1rocu.tacz.TaCZFabric", remap = false)
public abstract class TaczInitMixin {
    @Inject(method = "onInitialize", at = @At("RETURN"), require = 0)
    private void godvillagers$registerTaczEvents(CallbackInfo ci) {
        TaczDirectEventRuntime.registerEvents();
    }
}
