package com.anjas.godvillagers;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class StormcallRuntime {
    private StormcallRuntime() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
            if (!(source.getEntity() instanceof Player player)) return true;
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty() || !StormcallApplyRuntime.hasStormcall(held)) return true;
            if (!(target.level() instanceof ServerLevel level)) return true;

            String command = "summon minecraft:lightning_bolt " + target.getX() + " " + target.getY() + " " + target.getZ();
            level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withSuppressedOutput(), command);
            return true;
        });
    }
}
