package com.anjas.godvillagers.mixin;

import com.anjas.godvillagers.TaczEnchantRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Event-only death bridge: no ticking, no global scans, strict TACZ Magnet ownership. */
@Mixin(LivingEntity.class)
public abstract class TaczMagnetDeathMixin {
    private UUID godvillagers$magnetShooter;
    private Set<Integer> godvillagers$itemsBefore;
    private Set<Integer> godvillagers$xpBefore;

    @Inject(method = "die", at = @At("HEAD"), require = 0)
    private void godvillagers$resolveMagnetOwner(DamageSource source, CallbackInfo ci) {
        godvillagers$clearCapture();
        LivingEntity victim = (LivingEntity)(Object)this;
        UUID shooterId = TaczEnchantRuntime.consumeMagnetFatalShooter(victim);
        if (shooterId == null || !(victim.level() instanceof ServerLevel level)) return;
        ServerPlayer shooter = level.getServer().getPlayerList().getPlayer(shooterId);
        if (shooter == null) return;

        godvillagers$magnetShooter = shooterId;
        AABB box = victim.getBoundingBox().inflate(2.0D);
        godvillagers$itemsBefore = new HashSet<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) godvillagers$itemsBefore.add(entity.getId());
        godvillagers$xpBefore = new HashSet<>();
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) godvillagers$xpBefore.add(orb.getId());
    }

    @Inject(method = "die", at = @At("RETURN"), require = 0)
    private void godvillagers$deliverMagnetReward(DamageSource source, CallbackInfo ci) {
        LivingEntity victim = (LivingEntity)(Object)this;
        UUID shooterId = godvillagers$magnetShooter;
        Set<Integer> oldItems = godvillagers$itemsBefore;
        Set<Integer> oldXp = godvillagers$xpBefore;
        godvillagers$clearCapture();
        if (shooterId == null || !(victim.level() instanceof ServerLevel level)) return;
        ServerPlayer shooter = level.getServer().getPlayerList().getPlayer(shooterId);
        if (shooter == null) return;

        AABB box = victim.getBoundingBox().inflate(2.0D);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (oldItems == null || !oldItems.contains(entity.getId())) entity.teleportTo(shooter.getX(), shooter.getY(), shooter.getZ());
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
            if (oldXp == null || !oldXp.contains(orb.getId())) orb.teleportTo(shooter.getX(), shooter.getY(), shooter.getZ());
        }
    }

    private void godvillagers$clearCapture() {
        godvillagers$magnetShooter = null;
        godvillagers$itemsBefore = null;
        godvillagers$xpBefore = null;
    }
}
