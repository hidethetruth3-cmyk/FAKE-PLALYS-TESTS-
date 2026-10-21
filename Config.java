package com.fakeplayermod;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** config/fakeplayer.properties */
public final class Config {
    /** Trim colour on helmet, leggings and boots (all ranks). */
    public static String trimMaterial = "minecraft:netherite";
    public static String trimPattern = "minecraft:flow";
    /** Chestplate trim colour per rank: normal = netherite, general = red, commander = white. */
    public static String chestNormal = "minecraft:netherite";
    public static String chestGeneral = "minecraft:redstone";
    public static String chestCommander = "minecraft:quartz";
    public static int commanderLength = 14;
    public static double walkFraction = 0.5;
    public static int maxActive = 5000;
    /** Max milliseconds per server tick spent spawning queued bots (keeps big spawns from freezing the server). */
    public static int spawnMsPerTick = 20;
    public static int jumpMinSeconds = 3;
    public static int jumpMaxSeconds = 5;

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("fakeplayer.properties");
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                p.load(r);
            } catch (IOException e) {
                FakePlayerMod.LOGGER.warn("Could not read config", e);
            }
        }
        trimMaterial = p.getProperty("trim_material", trimMaterial);
        trimPattern = p.getProperty("trim_pattern", trimPattern);
        chestNormal = p.getProperty("chest_normal", chestNormal);
        chestGeneral = p.getProperty("chest_general", chestGeneral);
        chestCommander = p.getProperty("chest_commander", chestCommander);
        commanderLength = Math.max(3, Math.min(16, intOf(p, "commander_length", commanderLength)));
        try {
            walkFraction = Math.max(0, Math.min(1, Double.parseDouble(p.getProperty("walk_fraction", Double.toString(walkFraction)).trim())));
        } catch (NumberFormatException ignored) {
        }
        maxActive = Math.max(1, intOf(p, "max_fake_players", maxActive));
        spawnMsPerTick = Math.max(1, intOf(p, "spawn_ms_per_tick", spawnMsPerTick));
        p.remove("max_bots");
        jumpMinSeconds = intOf(p, "jump_min_seconds", jumpMinSeconds);
        jumpMaxSeconds = Math.max(jumpMinSeconds, intOf(p, "jump_max_seconds", jumpMaxSeconds));

        p.setProperty("trim_material", trimMaterial);
        p.setProperty("trim_pattern", trimPattern);
        p.setProperty("chest_normal", chestNormal);
        p.setProperty("chest_general", chestGeneral);
        p.setProperty("chest_commander", chestCommander);
        p.setProperty("commander_length", Integer.toString(commanderLength));
        p.setProperty("walk_fraction", Double.toString(walkFraction));
        p.setProperty("max_fake_players", Integer.toString(maxActive));
        p.setProperty("spawn_ms_per_tick", Integer.toString(spawnMsPerTick));
        p.setProperty("jump_min_seconds", Integer.toString(jumpMinSeconds));
        p.setProperty("jump_max_seconds", Integer.toString(jumpMaxSeconds));
        try (Writer w = Files.newBufferedWriter(file)) {
            p.store(w, "Fake Players config. Default gear only: custom loadouts you edit in-game are in fakeplayer_loadouts.json and override it.");
        } catch (IOException e) {
            FakePlayerMod.LOGGER.warn("Could not write config", e);
        }
    }

    private static int intOf(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, Integer.toString(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
    private Config() {}
}
