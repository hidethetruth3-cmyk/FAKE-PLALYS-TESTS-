package com.fakeplayermod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A 45-slot view (see Layout) onto a bot's inventory or onto a rank template. */
public class EditorContainer implements Container {
    public interface Backend {
        ItemStack get(int slot);
        void set(int slot, ItemStack stack);
        boolean valid();
    }

    private final ServerPlayer viewer;
    private final Backend backend;
    private final Runnable onChange;

    public EditorContainer(ServerPlayer viewer, Backend backend, Runnable onChange) {
        this.viewer = viewer;
        this.backend = backend;
        this.onChange = onChange;
    }

    @Override
    public int getContainerSize() {
        return Layout.SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < Layout.SAVED; i++) {
            if (!backend.get(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot < Layout.EXTRA_START ? backend.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot >= Layout.EXTRA_START) return ItemStack.EMPTY;
        ItemStack s = backend.get(slot);
        if (s.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = s.split(amount);
        backend.set(slot, s.isEmpty() ? ItemStack.EMPTY : s);
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot >= Layout.EXTRA_START) return ItemStack.EMPTY;
        ItemStack s = backend.get(slot);
        backend.set(slot, ItemStack.EMPTY);
        return s;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= Layout.EXTRA_START) {
            // the 4 unused slots: hand anything placed there straight back to the viewer
            if (!stack.isEmpty()) viewer.getInventory().placeItemBackInInventory(stack);
            return;
        }
        backend.set(slot, stack);
    }

    @Override
    public void setChanged() {
        onChange.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return backend.valid();
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < Layout.SAVED; i++) backend.set(i, ItemStack.EMPTY);
    }
}
