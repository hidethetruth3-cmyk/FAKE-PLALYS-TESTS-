package com.fakeplayermod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Slot layout of the 5-row editor chest (45 slots):
 * 0-26 = inventory rows, 27-35 = hotbar, 36 helmet, 37 chestplate, 38 leggings, 39 boots, 40 offhand, 41-44 unused.
 */
public final class Layout {
    public static final int SIZE = 45;
    public static final int SAVED = 41;
    public static final int EXTRA_START = 41;

    private static final EquipmentSlot[] EQUIP = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND
    };

    public static ItemStack get(ServerPlayer bot, int slot) {
        if (slot < 27) return bot.getInventory().getItem(slot + 9);
        if (slot < 36) return bot.getInventory().getItem(slot - 27);
        if (slot < SAVED) return bot.getItemBySlot(EQUIP[slot - 36]);
        return ItemStack.EMPTY;
    }

    public static void set(ServerPlayer bot, int slot, ItemStack stack) {
        if (slot < 27) bot.getInventory().setItem(slot + 9, stack);
        else if (slot < 36) bot.getInventory().setItem(slot - 27, stack);
        else if (slot < SAVED) bot.setItemSlot(EQUIP[slot - 36], stack);
    }

    public static ItemStack[] emptyLayout() {
        ItemStack[] a = new ItemStack[SIZE];
        java.util.Arrays.fill(a, ItemStack.EMPTY);
        return a;
    }
    private Layout() {}
}
