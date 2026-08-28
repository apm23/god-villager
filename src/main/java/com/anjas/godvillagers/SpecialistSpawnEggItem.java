package com.anjas.godvillagers;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SpecialistSpawnEggItem extends Item {
    private static final String GOD_FISHING_ROD_BOOK = "{buy:{id:emerald,count:35},buyB:{id:book,count:15},sell:{id:enchanted_book,count:1,components:{custom_name:{text:\"God Fishing Rod\",color:\"aqua\",bold:true,italic:false},stored_enchantments:{luck_of_the_sea:6,lure:10,unbreaking:3,mending:2}}},maxUses:999999,rewardExp:0b,priceMultiplier:0f}";
    private static final String GOD_SHIELD_BOOK = "{buy:{id:emerald,count:35},buyB:{id:book,count:15},sell:{id:enchanted_book,count:1,components:{custom_name:{text:\"God Shield\",color:\"dark_red\",bold:true,italic:false},stored_enchantments:{unbreaking:4,mending:3,thorns:5}}},maxUses:999999,rewardExp:0b,priceMultiplier:0f}";

    private final String summonSuffix;

    public SpecialistSpawnEggItem(Properties properties, String summonSuffix) {
        super(properties);
        this.summonSuffix = enhance(summonSuffix);
    }

    private static String enhance(String original) {
        // Alpha.82: no permanent outline and no always-visible name through walls.
        String result = original
            .replace("CustomNameVisible:1b,Glowing:1b,", "CustomNameVisible:0b,Glowing:0b,")
            .replace("Glowing:1b,", "Glowing:0b,")
            .replace("CustomNameVisible:1b,", "CustomNameVisible:0b,");

        if (result.contains("Tool Sage")) {
            result = appendTrade(result, GOD_FISHING_ROD_BOOK);
        }
        if (result.contains("Arms Sage")) {
            result = appendTrade(result, GOD_SHIELD_BOOK);
        }
        return result;
    }

    private static String appendTrade(String command, String trade) {
        int recipesEnd = command.lastIndexOf("]}}");
        if (recipesEnd < 0) {
            throw new IllegalArgumentException("Villager summon command has no Offers.Recipes terminator");
        }
        return command.substring(0, recipesEnd) + "," + trade + command.substring(recipesEnd);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 pos = new Vec3(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
        CommandSourceStack source = serverLevel.getServer().createCommandSourceStack()
            .withLevel(serverLevel)
            .withPosition(pos)
            .withSuppressedOutput();

        int split = summonSuffix.indexOf(' ');
        String entity = split < 0 ? summonSuffix : summonSuffix.substring(0, split);
        String nbt = split < 0 ? "" : summonSuffix.substring(split + 1);
        String command = "summon " + entity + " " + spawnPos.getX() + " " + spawnPos.getY() + " " + spawnPos.getZ();
        if (!nbt.isEmpty()) command += " " + nbt;
        serverLevel.getServer().getCommands().performPrefixedCommand(source, command);
        return InteractionResult.SUCCESS;
    }
}
