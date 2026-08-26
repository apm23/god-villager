package com.anjas.godvillagers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public final class SpecialistSpawnEggItem extends Item {
    private final String summonSuffix;

    public SpecialistSpawnEggItem(Properties properties, String summonSuffix) {
        super(properties);
        this.summonSuffix = summonSuffix;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 at = new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        var source = level.getServer().createCommandSourceStack().withLevel(level).withPosition(at).withSuppressedOutput();

        int split = summonSuffix.indexOf(' ');
        String entity = split < 0 ? summonSuffix : summonSuffix.substring(0, split);
        String nbt = split < 0 ? "" : summonSuffix.substring(split + 1);
        String command = "summon " + entity + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        if (!nbt.isEmpty()) command += " " + nbt;

        level.getServer().getCommands().performPrefixedCommand(source, command);
        return InteractionResult.SUCCESS;
    }
}
