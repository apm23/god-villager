package com.anjas.godvillagers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public final class StormcallApplyRuntime {
    private static final String BOOK_KEY = "godvillagers_stormcall_book";
    private static final String ITEM_KEY = "godvillagers_stormcall";
    private static final String VISUAL_KEY = "godvillagers_stormcall_visual";
    private static int ticks;

    private StormcallApplyRuntime() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks < 4) return;
            ticks = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!(player.containerMenu instanceof AnvilMenu menu)) continue;
                ItemStack base = menu.getSlot(0).getItem();
                ItemStack book = menu.getSlot(1).getItem();
                if (base.isEmpty() || book.isEmpty() || !isStormcallBook(book) || hasStormcall(base)) continue;

                ItemStack result = base.copy();
                applyStormcall(result);
                menu.getSlot(2).set(result);
                menu.setData(0, 1);
                setRepairItemCountCost(menu, 1);
                menu.broadcastChanges();
            }
        });
    }

    public static boolean isStormcallBook(ItemStack stack) {
        return getFlag(stack, BOOK_KEY);
    }

    public static boolean hasStormcall(ItemStack stack) {
        return getFlag(stack, ITEM_KEY);
    }

    private static boolean getFlag(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBoolean(key).orElse(false);
    }

    public static void applyStormcall(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(ITEM_KEY, true);
        tag.putBoolean(VISUAL_KEY, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        Component lore = makeText("★ Stormcall I");
        if (lore != null) stack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
    }

    private static void setRepairItemCountCost(AnvilMenu menu, int value) {
        try {
            Field field = AnvilMenu.class.getDeclaredField("repairItemCountCost");
            field.setAccessible(true);
            field.setInt(menu, value);
            return;
        } catch (Throwable ignored) {}

        try {
            for (Field field : AnvilMenu.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != Integer.TYPE) continue;
                field.setAccessible(true);
                field.setInt(menu, value);
                return;
            }
        } catch (Throwable ignored) {}
    }

    private static Component makeText(String text) {
        try {
            for (Method method : Component.class.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) continue;
                if (method.getParameterTypes()[0] != String.class) continue;
                if (!Component.class.isAssignableFrom(method.getReturnType())) continue;
                method.setAccessible(true);
                Object result = method.invoke(null, text);
                if (result instanceof Component component) return component;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
