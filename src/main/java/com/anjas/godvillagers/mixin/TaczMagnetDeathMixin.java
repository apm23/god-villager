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

/** Death-side bridge for strict TACZ Magnet ownership and reward capture. */
@Mixin(LivingEntity.class)
public abstract class TaczMagnetDeathMixin {
    private UUID godvillagers$magnetShooter;
    private Set<Integer> godvillagers$itemsBefore;
    private Set<Integer> godvillagers$xpBefore;

    @Inject(method = "die", at = @At("HEAD"), require = 0)
    private void godvillagers$resolveMagnetOwner(DamageSource source, CallbackInfo ci) {
        LivingEntity victim = (LivingEntity)(Object)this;
        UUID shooterId = TaczEnchantRuntime.consumeMagnetFatalShooter(victim);
        if (shooterId == null || !(victim.level() instanceof ServerLevel level)) return;
        ServerPlayer shooter = level.getServer().getPlayerList().getPlayer(shooterId);
        if (shooter == null) return;
        godvillagers$magnetShooter = shooterId;
        AABB box = victim.getBoundingBox().inflate(2.0D);
        godvillagers$itemsBefore = new HashSet<>();
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) godvillagers$itemsBefore.add(e.getId());
        godvillagers$xpBefore = new HashSet<>();
        for (ExperienceOrb e : level.getEntitiesOfClass(ExperienceOrb.class, box)) godvillagers$xpBefore.add(e.getId());
    }

    @Inject(method = "die", at = @At("RETURN"), require = 0)
    private void godvillagers$deliverMagnetReward(DamageSource source, CallbackInfo ci) {
        LivingEntity victim = (LivingEntity)(Object)this;
        UUID shooterId = godvillagers$magnetShooter;
        if (shooterId == null || !(victim.level() instanceof ServerLevel level)) return;
        godvillagers$magnetShooter = null;
        ServerPlayer shooter = level.getServer().getPlayerList().getPlayer(shooterId);
        if (shooter == null) return;
        AABB box = victim.getBoundingBox().inflate(2.0D);
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (godvillagers$itemsBefore == null || !godvillagers$itemsBefore.contains(e.getId())) e.teleportTo(shooter.getX(), shooter.getY(), shooter.getZ());
        }
        for (ExperienceOrb e : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
            if (godvillagers$xpBefore == null || !godvillagers$xpBefore.contains(e.getId())) e.teleportTo(shooter.getX(), shooter.getY(), shooter.getZ());
        }
        godvillagers$itemsBefore = null;
        godvillagers$xpBefore = null;
    }
}
