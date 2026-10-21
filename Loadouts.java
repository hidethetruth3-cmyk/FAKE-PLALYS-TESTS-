package com.fakeplayermod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Custom gear per rank, stored in config/fakeplayer_loadouts.json.
 * It lives in the config folder (not in a world), so it is shared by every world.
 */
public final class Loadouts {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static JsonObject data;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("fakeplayer_loadouts.json");
    }

    private static synchronized JsonObject data() {
        if (data == null) {
            data = new JsonObject();
            Path f = file();
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f)) {
                    JsonElement e = JsonParser.parseReader(r);
                    if (e.isJsonObject()) data = e.getAsJsonObject();
                } catch (Exception ex) {
                    FakePlayerMod.LOGGER.warn("Could not read loadouts", ex);
                }
            }
        }
        return data;
    }

    private static void write() {
        try (Writer w = Files.newBufferedWriter(file())) {
            GSON.toJson(data(), w);
        } catch (Exception e) {
            FakePlayerMod.LOGGER.warn("Could not write loadouts", e);
        }
    }

    public static synchronized boolean has(Rank rank) {
        return data().has(rank.id);
    }

    /** The saved layout for a rank (45 slots, empty slots are ItemStack.EMPTY). */
    public static synchronized ItemStack[] get(MinecraftServer server, Rank rank) {
        ItemStack[] out = Layout.emptyLayout();
        JsonElement el = data().get(rank.id);
        if (el == null || !el.isJsonObject()) return out;
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            final int slot;
            try {
                slot = Integer.parseInt(e.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            if (slot < 0 || slot >= Layout.SAVED) continue;
            ItemStack.CODEC.parse(ops, e.getValue())
                    .resultOrPartial(err -> FakePlayerMod.LOGGER.warn("Bad saved item in slot {}: {}", slot, err))
                    .ifPresent(s -> out[slot] = s);
        }
        return out;
    }

    public static synchronized void save(MinecraftServer server, Rank rank, ItemStack[] layout) {
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        JsonObject o = new JsonObject();
        for (int i = 0; i < Layout.SAVED; i++) {
            ItemStack s = layout[i];
            if (s == null || s.isEmpty()) continue;
            final int idx = i;
            ItemStack.CODEC.encodeStart(ops, s)
                    .resultOrPartial(err -> FakePlayerMod.LOGGER.warn("Could not save slot {}: {}", idx, err))
                    .ifPresent(j -> o.add(Integer.toString(idx), j));
        }
        data().add(rank.id, o);
        write();
    }

    public static void saveFromBot(MinecraftServer server, FakePlayer bot) {
        ItemStack[] l = Layout.emptyLayout();
        for (int i = 0; i < Layout.SAVED; i++) l[i] = Layout.get(bot, i).copy();
        save(server, bot.getRank(), l);
    }

    public static synchronized void reset(Rank rank) {
        data().remove(rank.id);
        write();
    }

    /** Puts the saved loadout on a bot. Returns false if this rank has no saved loadout. */
    public static boolean apply(MinecraftServer server, ServerPlayer bot, Rank rank) {
        if (!has(rank)) return false;
        applyLayout(bot, get(server, rank));
        return true;
    }

    public static void applyLayout(ServerPlayer bot, ItemStack[] layout) {
        bot.getInventory().clearContent();
        for (int i = 0; i < Layout.SAVED; i++) {
            if (!layout[i].isEmpty()) Layout.set(bot, i, layout[i].copy());
        }
    }
    private Loadouts() {}
}
