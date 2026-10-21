package com.fakeplayermod;

import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Default gear: netherite armor, Flow trim, Protection IV + Unbreaking III. A saved loadout replaces it. */
public final class Gear {
    public static void equip(ServerPlayer p, MinecraftServer server, Rank rank) {
        if (Loadouts.apply(server, p, rank)) return;
        Loadouts.applyLayout(p, defaultLayout(server, rank));
    }

    public static String chestMaterial(Rank rank) {
        return switch (rank) {
            case NORMAL -> Config.chestNormal;
            case GENERAL -> Config.chestGeneral;
            case COMMANDER -> Config.chestCommander;
        };
    }

    /** The regular gear for a rank, in editor layout. */
    public static ItemStack[] defaultLayout(MinecraftServer server, Rank rank) {
        ItemStack[] l = Layout.emptyLayout();
        l[36] = make(server, "minecraft:netherite_helmet", Config.trimMaterial);
        l[37] = make(server, "minecraft:netherite_chestplate", chestMaterial(rank));
        l[38] = make(server, "minecraft:netherite_leggings", Config.trimMaterial);
        l[39] = make(server, "minecraft:netherite_boots", Config.trimMaterial);
        return l;
    }

    private static ItemStack make(MinecraftServer server, String id, String material) {
        Optional<ItemStack> full = parse(server, tag(id, material));
        if (full.isPresent()) return full.get();
        FakePlayerMod.LOGGER.warn("Could not build {} with trim '{}' / '{}', trying without the trim", id, material, Config.trimPattern);
        return parse(server, tag(id, null)).orElse(ItemStack.EMPTY);
    }

    private static CompoundTag tag(String id, String material) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", id);
        stack.putInt("count", 1);
        CompoundTag comps = new CompoundTag();
        CompoundTag ench = new CompoundTag();
        ench.putInt("minecraft:protection", 4);
        ench.putInt("minecraft:unbreaking", 3);
        comps.put("minecraft:enchantments", ench);
        if (material != null) {
            CompoundTag t = new CompoundTag();
            t.putString("material", material);
            t.putString("pattern", Config.trimPattern);
            comps.put("minecraft:trim", t);
        }
        stack.put("components", comps);
        return stack;
    }

    private static Optional<ItemStack> parse(MinecraftServer server, CompoundTag tag) {
        DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        return ItemStack.CODEC.parse(ops, tag).result();
    }
    private Gear() {}
}
