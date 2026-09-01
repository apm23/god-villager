package com.anjas.godvillagers;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class SpecialistSpawnEggItem extends Item {
    private static final String GOD_FISHING_ROD_BOOK = "{buy:{id:emerald,count:35},buyB:{id:book,count:15},sell:{id:enchanted_book,count:1,components:{custom_name:{text:\"God Fishing Rod\",color:\"aqua\",bold:true,italic:false},stored_enchantments:{luck_of_the_sea:6,lure:10,unbreaking:3,mending:2}}},maxUses:999999,rewardExp:0b,priceMultiplier:0f}";
    private static final String GOD_SHIELD_BOOK = "{buy:{id:emerald,count:35},buyB:{id:book,count:15},sell:{id:enchanted_book,count:1,components:{custom_name:{text:\"God Shield\",color:\"dark_red\",bold:true,italic:false},stored_enchantments:{unbreaking:4,mending:3,thorns:5}}},maxUses:999999,rewardExp:0b,priceMultiplier:0f}";

    private final String summonSuffix;

    public SpecialistSpawnEggItem(Properties properties, String summonSuffix) {
        super(properties);
        this.summonSuffix = enhance(summonSuffix);
    }

    private static String enhance(String original) {
        // Alpha.82+: no permanent outline and no always-visible name through walls.
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

        // Keep every enchanted-book offer together at the top of every specialist's
        // trade list. This is intentionally a stable partition: existing book order
        // is preserved, and non-book/random recipes keep their relative order below.
        return booksFirst(result);
    }

    private static String appendTrade(String command, String trade) {
        int recipesEnd = command.lastIndexOf("]}}");
        if (recipesEnd < 0) {
            throw new IllegalArgumentException("Villager summon command has no Offers.Recipes terminator");
        }
        return command.substring(0, recipesEnd) + "," + trade + command.substring(recipesEnd);
    }

    private static String booksFirst(String command) {
        final String marker = "Recipes:[";
        int markerPos = command.indexOf(marker);
        if (markerPos < 0) return command;

        int listStart = markerPos + marker.length();
        int listEnd = findMatchingRecipesEnd(command, listStart);
        if (listEnd < 0) {
            throw new IllegalArgumentException("Villager summon command has malformed Offers.Recipes list");
        }

        String body = command.substring(listStart, listEnd);
        List<String> recipes = splitTopLevelRecipes(body);
        if (recipes.size() < 2) return command;

        List<String> books = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (String recipe : recipes) {
            if (isEnchantedBookTrade(recipe)) books.add(recipe);
            else other.add(recipe);
        }
        if (books.isEmpty() || other.isEmpty()) return command;

        List<String> ordered = new ArrayList<>(recipes.size());
        ordered.addAll(books);
        ordered.addAll(other);
        return command.substring(0, listStart) + String.join(",", ordered) + command.substring(listEnd);
    }

    private static int findMatchingRecipesEnd(String text, int contentStart) {
        int squareDepth = 0;
        int curlyDepth = 0;
        boolean quoted = false;
        boolean escaped = false;

        for (int i = contentStart; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '"') {
                quoted = true;
                continue;
            }
            if (c == '{') curlyDepth++;
            else if (c == '}') curlyDepth--;
            else if (c == '[') squareDepth++;
            else if (c == ']') {
                if (squareDepth == 0 && curlyDepth == 0) return i;
                squareDepth--;
            }
        }
        return -1;
    }

    private static List<String> splitTopLevelRecipes(String body) {
        List<String> out = new ArrayList<>();
        int start = 0;
        int curlyDepth = 0;
        int squareDepth = 0;
        boolean quoted = false;
        boolean escaped = false;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '"') {
                quoted = true;
                continue;
            }
            if (c == '{') curlyDepth++;
            else if (c == '}') curlyDepth--;
            else if (c == '[') squareDepth++;
            else if (c == ']') squareDepth--;
            else if (c == ',' && curlyDepth == 0 && squareDepth == 0) {
                out.add(body.substring(start, i));
                start = i + 1;
            }
        }
        out.add(body.substring(start));
        return out;
    }

    private static boolean isEnchantedBookTrade(String recipe) {
        String compact = recipe.replace(" ", "");
        return compact.contains("sell:{id:enchanted_book,")
            || compact.contains("sell:{id:\"minecraft:enchanted_book\",")
            || compact.contains("id:minecraft:enchanted_book");
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
