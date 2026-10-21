package com.fakeplayermod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.fakeplayermod.Formation.Shape;
import com.fakeplayermod.Formation.Slot;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;

public class FakePlayerMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("fakeplayer");

    /** Global jump setting: true = every bot jumps every 3-5 s, false = they stand still. */
    public static volatile boolean jumping = false;

    /** Body-move mode: bots shuffle their whole body around their spot (no looking around). */
    public static volatile boolean headMoving = false;

    /** Battle mode: bots look up a bit and swing at the air. */
    public static volatile boolean battling = false;

    // big spawns are spread over many ticks so the server never freezes
    private static final ArrayDeque<Runnable> SPAWN_QUEUE = new ArrayDeque<>();
    private static final Set<String> RESERVED = new HashSet<>();
    private static int pending = 0;

    private static final List<String> NAMES = new ArrayList<>();
    private static final double RAD = 57.29577951308232;

    // Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east
    private static final Map<String, Float> DIRECTIONS = new LinkedHashMap<>();
    static {
        DIRECTIONS.put("south", 0F);
        DIRECTIONS.put("southwest", 45F);
        DIRECTIONS.put("west", 90F);
        DIRECTIONS.put("northwest", 135F);
        DIRECTIONS.put("north", 180F);
        DIRECTIONS.put("northeast", 225F);
        DIRECTIONS.put("east", 270F);
        DIRECTIONS.put("southeast", 315F);
    }

    @Override
    public void onInitialize() {
        Config.load();
        try (InputStream in = FakePlayerMod.class.getResourceAsStream("/data/fakeplayer/names.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) NAMES.add(line);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Could not load names", e);
        }
        LOGGER.info("Loaded {} fake player names", NAMES.size());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(server -> drainSpawns());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SPAWN_QUEUE.clear();
            RESERVED.clear();
            pending = 0;
        });
    }

    private static void drainSpawns() {
        if (SPAWN_QUEUE.isEmpty()) return;
        long end = System.nanoTime() + Config.spawnMsPerTick * 1_000_000L;
        do {
            Runnable r = SPAWN_QUEUE.poll();
            if (r == null) break;
            r.run();
        } while (System.nanoTime() < end);
    }

    private static void queueSpawn(String name, Runnable task) {
        RESERVED.add(name);
        pending++;
        SPAWN_QUEUE.add(() -> {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.error("Failed to spawn " + name, e);
            } finally {
                RESERVED.remove(name);
                pending--;
            }
        });
    }

    private static boolean isFree(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayerByName(name) == null && !RESERVED.contains(name);
    }

    // ------------------------------------------------------------------ commands

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fakeplayer");

        root.then(Commands.literal("spawn")
                .executes(c -> spawnMany(c, Rank.NORMAL, 1))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 5000))
                        .executes(c -> spawnMany(c, Rank.NORMAL, IntegerArgumentType.getInteger(c, "count"))))
                .then(Commands.literal("name")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(c -> spawnNamed(c, StringArgumentType.getString(c, "name"))))));

        root.then(Commands.literal("mass")
                .then(Commands.literal("spawn").then(countNode(Rank.NORMAL)).then(customNode(Rank.NORMAL))));

        LiteralArgumentBuilder<CommandSourceStack> rank = Commands.literal("rank");
        LiteralArgumentBuilder<CommandSourceStack> rankSpawn = Commands.literal("spawn");
        LiteralArgumentBuilder<CommandSourceStack> rankMassSpawn = Commands.literal("spawn");
        for (Rank r : Rank.values()) {
            rankSpawn.then(Commands.literal(r.id)
                    .executes(c -> spawnMany(c, r, 1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 5000))
                            .executes(c -> spawnMany(c, r, IntegerArgumentType.getInteger(c, "count")))));
            rankMassSpawn.then(Commands.literal(r.id).then(countNode(r)).then(customNode(r)));
        }
        rank.then(rankSpawn);
        rank.then(Commands.literal("mass").then(rankMassSpawn));
        root.then(rank);

        LiteralArgumentBuilder<CommandSourceStack> look = Commands.literal("look");
        for (Map.Entry<String, Float> e : DIRECTIONS.entrySet()) {
            float yaw = e.getValue();
            look.then(Commands.literal(e.getKey()).executes(c -> lookYaw(c, yaw, 0.0F)));
        }
        look.then(Commands.literal("player").executes(FakePlayerMod::lookPlayer));
        look.then(Commands.literal("same").executes(c ->
                lookYaw(c, c.getSource().getRotation().y, c.getSource().getRotation().x)));
        root.then(look);

        root.then(Commands.literal("head")
                .then(Commands.literal("move").executes(c -> setHead(c, true)))
                .then(Commands.literal("stop").executes(c -> setHead(c, false))));

        // /fakeplayer march 10s | 1m | <seconds> | stop  (walk straight ahead the way they face)
        root.then(Commands.literal("march")
                .then(Commands.literal("10s").executes(c -> startMarch(c, 10)))
                .then(Commands.literal("1m").executes(c -> startMarch(c, 60)))
                .then(Commands.literal("stop").executes(c -> startMarch(c, 0)))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                        .executes(c -> startMarch(c, IntegerArgumentType.getInteger(c, "seconds")))));

        root.then(Commands.literal("battle")
                .then(Commands.literal("start").executes(c -> setBattle(c, true)))
                .then(Commands.literal("stop").executes(c -> setBattle(c, false))));

        root.then(Commands.literal("follow")
                .then(Commands.literal("start").executes(FakePlayerMod::followStart))
                .then(Commands.literal("stop").executes(FakePlayerMod::followStop)));

        root.then(Commands.literal("inv")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(FakePlayerMod::suggestBots)
                        .executes(c -> openBotInv(c, StringArgumentType.getString(c, "name")))));

        LiteralArgumentBuilder<CommandSourceStack> loadout = Commands.literal("loadout");
        LiteralArgumentBuilder<CommandSourceStack> loadoutReset = Commands.literal("reset");
        for (Rank r : Rank.values()) {
            loadout.then(Commands.literal(r.id).executes(c -> openRankEditor(c, r)));
            loadoutReset.then(Commands.literal(r.id).executes(c -> resetLoadout(c, r)));
        }
        loadout.then(loadoutReset);
        root.then(loadout);

        root.then(Commands.literal("jump")
                .then(Commands.literal("continue").executes(c -> setJumping(c, true)))
                .then(Commands.literal("stop").executes(c -> setJumping(c, false)))
                .then(Commands.literal("once").executes(FakePlayerMod::jumpOnce)));
        root.then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(FakePlayerMod::suggestBots)
                        .executes(c -> remove(c, StringArgumentType.getString(c, "name")))));
        root.then(Commands.literal("removeall").executes(FakePlayerMod::removeAll));
        root.then(Commands.literal("list").executes(FakePlayerMod::list));

        d.register(root);
    }

    /** Shapes: spawn straight into place by default; add "walk" to have about half of them walk in. */
    private static RequiredArgumentBuilder<CommandSourceStack, Integer> countNode(Rank rank) {
        return Commands.argument("count", IntegerArgumentType.integer(1, 5000))
                .executes(c -> massSpawn(c, rank, Shape.RANDOM, false))
                .then(Commands.literal("random").executes(c -> massSpawn(c, rank, Shape.RANDOM, false)))
                .then(shapeNode("square", rank, Shape.SQUARE))
                .then(Commands.literal("aisle")
                        .executes(c -> massAisle(c, rank, 2, 1, 3))
                        .then(spacingNode((c, sp, rows, gap) -> massAisle(c, rank, sp, rows, gap))))
                .then(Commands.literal("lines")
                        .executes(c -> massSpawn(c, rank, Shape.LINES_FORWARD, false))
                        .then(Commands.literal("walk").executes(c -> massSpawn(c, rank, Shape.LINES_FORWARD, true)))
                        .then(shapeNode("forward", rank, Shape.LINES_FORWARD))
                        .then(shapeNode("facing", rank, Shape.LINES_FACING)));
    }

    private interface AisleRun {
        int run(CommandContext<CommandSourceStack> c, int spacing, int rows, int gap);
    }

    /** [spacing] [rows] [gap]: blocks between bots, lines per side, empty blocks in the middle (0 = no gap). */
    private static RequiredArgumentBuilder<CommandSourceStack, Integer> spacingNode(AisleRun run) {
        return Commands.argument("spacing", IntegerArgumentType.integer(1, 10))
                .executes(c -> run.run(c, IntegerArgumentType.getInteger(c, "spacing"), 1, 3))
                .then(Commands.argument("rows", IntegerArgumentType.integer(1, 100))
                        .executes(c -> run.run(c, IntegerArgumentType.getInteger(c, "spacing"),
                                IntegerArgumentType.getInteger(c, "rows"), 3))
                        .then(Commands.argument("gap", IntegerArgumentType.integer(0, 20))
                                .executes(c -> run.run(c, IntegerArgumentType.getInteger(c, "spacing"),
                                        IntegerArgumentType.getInteger(c, "rows"),
                                        IntegerArgumentType.getInteger(c, "gap")))));
    }

    /** custom <left> <right> [spacing] [rows] [gap] */
    private static LiteralArgumentBuilder<CommandSourceStack> customNode(Rank rank) {
        return Commands.literal("custom")
                .then(Commands.argument("left", IntegerArgumentType.integer(0, 2500))
                        .then(Commands.argument("right", IntegerArgumentType.integer(0, 2500))
                                .executes(c -> massCustom(c, rank, 2, 1, 3))
                                .then(spacingNode((c, sp, rows, gap) -> massCustom(c, rank, sp, rows, gap)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> shapeNode(String lit, Rank rank, Shape shape) {
        return Commands.literal(lit)
                .executes(c -> massSpawn(c, rank, shape, false))
                .then(Commands.literal("walk").executes(c -> massSpawn(c, rank, shape, true)));
    }

    // ------------------------------------------------------------------ names

    private static List<FakePlayer> bots(MinecraftServer server) {
        List<FakePlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p instanceof FakePlayer f) out.add(f);
        }
        return out;
    }

    /** A..Z in order, then the all-underscore one last. e.g. _______A______ (same length as the other names). */
    private static List<String> commanderNames() {
        int len = Config.commanderLength;
        int left = len / 2;
        int right = len - 1 - left;
        List<String> out = new ArrayList<>();
        for (char ch = 'A'; ch <= 'Z'; ch++) {
            out.add("_".repeat(left) + ch + "_".repeat(right));
        }
        out.add("_".repeat(len));
        return out;
    }

    private static int freeCommanderNames(MinecraftServer server) {
        int n = 0;
        for (String s : commanderNames()) {
            if (isFree(server, s)) n++;
        }
        return n;
    }

    private static final String NAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static String randomName(ThreadLocalRandom rnd) {
        int len = 8 + rnd.nextInt(7);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(NAME_CHARS.charAt(rnd.nextInt(NAME_CHARS.length())));
        return sb.toString();
    }

    private static String pickName(MinecraftServer server, Rank rank) {
        if (rank == Rank.COMMANDER) {
            for (String s : commanderNames()) {
                if (isFree(server, s)) return s;
            }
            return null;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (!NAMES.isEmpty()) {
            for (int tries = 0; tries < 40; tries++) {
                String n = NAMES.get(rnd.nextInt(NAMES.size()));
                if (isFree(server, n)) return n;
            }
        }
        // list used up (5000 bots need more names than the list has): make a random one
        for (int tries = 0; tries < 200; tries++) {
            String n = randomName(rnd);
            if (isFree(server, n)) return n;
        }
        return null;
    }

    /** How many more bots of this rank may be spawned right now. */
    private static int capacity(MinecraftServer server, Rank rank, int requested) {
        int n = Math.min(requested, Config.maxActive - bots(server).size() - pending);
        if (rank == Rank.COMMANDER) n = Math.min(n, freeCommanderNames(server));
        return n;
    }

    private static boolean noRoom(CommandSourceStack src, Rank rank, int room) {
        if (room > 0) return false;
        src.sendFailure(Component.literal(rank == Rank.COMMANDER
                ? "No commander names left (there are 27: A-Z plus the all-underscore one) or max active reached."
                : "Max active fake players reached (" + Config.maxActive + ", see config/fakeplayer.properties)."));
        return true;
    }

    // ------------------------------------------------------------------ spawning

    private static int spawnMany(CommandContext<CommandSourceStack> c, Rank rank, int count) {
        CommandSourceStack src = c.getSource();
        MinecraftServer server = src.getServer();
        int n = capacity(server, rank, count);
        if (noRoom(src, rank, n)) return 0;
        int queued = 0;
        for (int i = 0; i < n; i++) {
            String name = pickName(server, rank);
            if (name == null) break;
            final int idx = i;
            queueSpawn(name, () -> spawnAround(src, name, idx, rank));
            queued++;
        }
        final int q = queued;
        src.sendSuccess(() -> Component.literal("Spawning " + q + " " + rank.id + " fake player(s)"
                + (q > 40 ? " (spread over a few seconds so the server doesn't freeze)." : ".")), false);
        return queued;
    }

    private static int spawnNamed(CommandContext<CommandSourceStack> c, String name) {
        CommandSourceStack src = c.getSource();
        MinecraftServer server = src.getServer();
        if (!isFree(server, name)) {
            src.sendFailure(Component.literal(name + " is already online."));
            return 0;
        }
        if (bots(server).size() + pending >= Config.maxActive) {
            src.sendFailure(Component.literal("Max active fake players reached."));
            return 0;
        }
        spawnAround(src, name, 0, Rank.NORMAL);
        src.sendSuccess(() -> Component.literal("Spawned " + name + "."), false);
        return 1;
    }

    private static boolean spawnAround(CommandSourceStack src, String name, int index, Rank rank) {
        ServerLevel level = src.getLevel();
        Vec3 origin = src.getPosition();
        double angle = index * 2.399963;
        double radius = 2.0 + 0.9 * Math.sqrt(index);
        Vec3 pos = safePos(level, origin.x + Math.cos(angle) * radius, origin.y, origin.z + Math.sin(angle) * radius);
        if (pos == null) pos = origin;
        float yaw = yawTo(pos, origin);
        return trySpawn(src.getServer(), level, name, pos, yaw, rank) != null;
    }

    private static int massSpawn(CommandContext<CommandSourceStack> c, Rank rank, Shape shape, boolean walk) {
        CommandSourceStack src = c.getSource();
        IntFunction<List<Slot>> maker = shape == Shape.RANDOM ? null
                : n -> Formation.slots(shape, n, src.getPosition(), src.getRotation().y);
        return runMass(src, rank, IntegerArgumentType.getInteger(c, "count"), maker, walk);
    }

    private static int massAisle(CommandContext<CommandSourceStack> c, Rank rank, int spacing, int rows, int gap) {
        CommandSourceStack src = c.getSource();
        return runMass(src, rank, IntegerArgumentType.getInteger(c, "count"),
                n -> Formation.aisle((n + 1) / 2, n / 2, spacing, rows, gap, src.getPosition(), src.getRotation().y), false);
    }

    private static int massCustom(CommandContext<CommandSourceStack> c, Rank rank, int spacing, int rows, int gap) {
        CommandSourceStack src = c.getSource();
        int left = IntegerArgumentType.getInteger(c, "left");
        int right = IntegerArgumentType.getInteger(c, "right");
        int total = left + right;
        if (total <= 0) {
            src.sendFailure(Component.literal("Left and right can't both be 0."));
            return 0;
        }
        return runMass(src, rank, total, n -> {
            int l = Math.min(n, (int) Math.round(n * (left / (double) total)));
            return Formation.aisle(l, n - l, spacing, rows, gap, src.getPosition(), src.getRotation().y);
        }, false);
    }

    /** maker == null means scatter randomly; otherwise it builds the slots for the (capped) number of bots. */
    private static int runMass(CommandSourceStack src, Rank rank, int requested, IntFunction<List<Slot>> maker, boolean walk) {
        MinecraftServer server = src.getServer();
        ServerLevel level = src.getLevel();
        Vec3 origin = src.getPosition();
        int n = capacity(server, rank, requested);
        if (noRoom(src, rank, n)) return 0;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Slot> slots = maker == null ? null : maker.apply(n);
        double cx = origin.x, cz = origin.z;
        if (slots != null) {
            cx = slots.stream().mapToDouble(Slot::x).average().orElse(origin.x);
            cz = slots.stream().mapToDouble(Slot::z).average().orElse(origin.z);
        }

        int queued = 0;
        for (int i = 0; i < n; i++) {
            String name = pickName(server, rank);
            if (name == null) break;
            final int idx = i;
            final double fcx = cx, fcz = cz;
            queueSpawn(name, () -> spawnOne(server, level, origin, rank, name, slots, idx, n, walk, fcx, fcz));
            queued++;
        }
        final int q = queued;
        src.sendSuccess(() -> Component.literal("Spawning " + q + " " + rank.id + " fake player(s)"
                + (walk ? " (about half walking into formation)" : "")
                + (q > 40 ? " - spread over a few seconds so the server doesn't freeze." : ".")), false);
        return queued;
    }

    private static void spawnOne(MinecraftServer server, ServerLevel level, Vec3 origin, Rank rank, String name,
                                 List<Slot> slots, int i, int total, boolean walk, double cx, double cz) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (slots == null) {
            Vec3 pos = scatterPoint(level, origin.x, origin.y, origin.z, 3.0, 4.0 + Math.sqrt(total) * 1.6);
            trySpawn(server, level, name, pos, rnd.nextFloat() * 360.0F, rank);
            return;
        }
        Slot s = slots.get(i);
        Vec3 slotPos = safePos(level, s.x(), origin.y, s.z());
        if (slotPos == null) slotPos = new Vec3(s.x(), origin.y, s.z());
        if (walk && rnd.nextDouble() < Config.walkFraction) {
            Vec3 start = scatterPoint(level, cx, origin.y, cz, 10.0, 18.0);
            FakePlayer p = trySpawn(server, level, name, start, yawTo(start, slotPos), rank);
            if (p != null) p.walkTo(slotPos, s.yaw());
        } else {
            trySpawn(server, level, name, slotPos, s.yaw(), rank);
        }
    }

    private static FakePlayer trySpawn(MinecraftServer server, ServerLevel level, String name, Vec3 pos, float yaw, Rank rank) {
        try {
            return FakePlayer.spawn(server, level, name, pos, yaw, rank);
        } catch (Exception e) {
            LOGGER.error("Failed to spawn " + name, e);
            return null;
        }
    }

    private static Vec3 scatterPoint(ServerLevel level, double cx, double cy, double cz, double minR, double maxR) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double ax = 0, az = 0;
        for (int tries = 0; tries < 12; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = minR + rnd.nextDouble() * (maxR - minR);
            ax = cx + Math.cos(a) * r;
            az = cz + Math.sin(a) * r;
            Vec3 p = safePos(level, ax, cy, az);
            if (p != null) return p;
        }
        return new Vec3(ax, cy, az);
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(-(to.x - from.x), to.z - from.z) * RAD);
    }

    private static Vec3 safePos(ServerLevel level, double x, double y, double z) {
        BlockPos base = BlockPos.containing(x, y, z);
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos feet = base.offset(0, dy, 0);
            if (clear(level, feet) && clear(level, feet.above()) && !clear(level, feet.below())) {
                return new Vec3(x, feet.getY(), z);
            }
        }
        return null;
    }

    private static boolean clear(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    // ------------------------------------------------------------------ look / jump / remove

    private static int lookYaw(CommandContext<CommandSourceStack> c, float yaw, float pitch) {
        List<FakePlayer> list = bots(c.getSource().getServer());
        for (FakePlayer f : list) {
            f.stopMoving();
            f.faceYawPitch(yaw, pitch);
        }
        c.getSource().sendSuccess(() -> Component.literal(list.size() + " fake player(s) turned."), false);
        return list.size();
    }

    /** One-time turn toward you (looks up or down at your eyes). They do not keep following you. */
    private static int lookPlayer(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        Vec3 target = src.getEntity() != null ? src.getEntity().getEyePosition() : src.getPosition().add(0, 1.62, 0);
        List<FakePlayer> list = bots(src.getServer());
        for (FakePlayer f : list) {
            f.stopMoving();
            f.lookAt(target);
        }
        src.sendSuccess(() -> Component.literal(list.size() + " fake player(s) turned toward you."), false);
        return list.size();
    }

    private static int setHead(CommandContext<CommandSourceStack> c, boolean on) {
        headMoving = on;
        for (FakePlayer f : bots(c.getSource().getServer())) {
            if (on) f.startSway();
            else f.stopSway();
        }
        c.getSource().sendSuccess(() -> Component.literal(on
                ? "Fake players now move their whole body (no looking around)."
                : "Fake players stopped moving."), false);
        return 1;
    }

    private static int startMarch(CommandContext<CommandSourceStack> c, int seconds) {
        List<FakePlayer> list = bots(c.getSource().getServer());
        for (FakePlayer f : list) f.startMarch(seconds * 20);
        c.getSource().sendSuccess(() -> Component.literal(seconds > 0
                ? list.size() + " fake player(s) marching forward for " + seconds + "s."
                : "Marching stopped."), false);
        return list.size();
    }

    private static int setBattle(CommandContext<CommandSourceStack> c, boolean on) {
        battling = on;
        List<FakePlayer> list = bots(c.getSource().getServer());
        for (FakePlayer f : list) {
            if (on) f.startBattle();
            else f.stopBattle();
        }
        c.getSource().sendSuccess(() -> Component.literal(on
                ? "Battle mode on: " + list.size() + " fake player(s) look up and swing at the air."
                : "Battle mode off."), false);
        return list.size();
    }

    private static int followStart(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer me = c.getSource().getPlayerOrException();
        List<FakePlayer> list = bots(c.getSource().getServer());
        for (FakePlayer f : list) f.follow(me.getUUID());
        c.getSource().sendSuccess(() -> Component.literal(list.size() + " fake player(s) are following you. Use /fakeplayer follow stop to stop."), false);
        return list.size();
    }

    private static int followStop(CommandContext<CommandSourceStack> c) {
        List<FakePlayer> list = bots(c.getSource().getServer());
        for (FakePlayer f : list) f.stopMoving();
        c.getSource().sendSuccess(() -> Component.literal("Fake players stopped following."), false);
        return list.size();
    }

    // ------------------------------------------------------------------ inventory / loadouts

    private static CompletableFuture<Suggestions> suggestBots(CommandContext<CommandSourceStack> c, SuggestionsBuilder b) {
        List<String> names = new ArrayList<>();
        for (FakePlayer f : bots(c.getSource().getServer())) names.add(f.getGameProfile().name());
        return SharedSuggestionProvider.suggest(names, b);
    }

    private static void openEditor(ServerPlayer viewer, EditorContainer container, String title) {
        viewer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x5, id, inv, container, 5),
                Component.literal(title)));
    }

    /** Opens a bot's real inventory. Whatever you change is also saved as the loadout for its rank. */
    private static int openBotInv(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        ServerPlayer viewer = src.getPlayerOrException();
        MinecraftServer server = src.getServer();
        for (FakePlayer bot : bots(server)) {
            if (!bot.getGameProfile().name().equalsIgnoreCase(name)) continue;
            EditorContainer container = new EditorContainer(viewer, new EditorContainer.Backend() {
                @Override
                public ItemStack get(int slot) {
                    return Layout.get(bot, slot);
                }

                @Override
                public void set(int slot, ItemStack stack) {
                    Layout.set(bot, slot, stack);
                }

                @Override
                public boolean valid() {
                    return !bot.isRemoved();
                }
            }, () -> Loadouts.saveFromBot(server, bot));
            openEditor(viewer, container, bot.getGameProfile().name() + " (" + bot.getRank().id + ")");
            return 1;
        }
        src.sendFailure(Component.literal("No fake player named " + name + "."));
        return 0;
    }

    /** Opens the gear template for a rank (no bot needed). New bots of that rank spawn with it. */
    private static int openRankEditor(CommandContext<CommandSourceStack> c, Rank rank) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        ServerPlayer viewer = src.getPlayerOrException();
        MinecraftServer server = src.getServer();
        ItemStack[] layout = Loadouts.has(rank) ? Loadouts.get(server, rank) : Gear.defaultLayout(server, rank);
        EditorContainer container = new EditorContainer(viewer, new EditorContainer.Backend() {
            @Override
            public ItemStack get(int slot) {
                return layout[slot];
            }

            @Override
            public void set(int slot, ItemStack stack) {
                layout[slot] = stack;
            }

            @Override
            public boolean valid() {
                return true;
            }
        }, () -> Loadouts.save(server, rank, layout));
        openEditor(viewer, container, rank.id + " loadout");
        return 1;
    }

    private static int resetLoadout(CommandContext<CommandSourceStack> c, Rank rank) {
        Loadouts.reset(rank);
        c.getSource().sendSuccess(() -> Component.literal(rank.id + " loadout reset to the default gear (affects new spawns)."), false);
        return 1;
    }

    private static int setJumping(CommandContext<CommandSourceStack> c, boolean on) {
        jumping = on;
        if (on) {
            for (FakePlayer f : bots(c.getSource().getServer())) f.restartJumpTimer();
        }
        c.getSource().sendSuccess(() -> Component.literal(on
                ? "Fake players now jump every " + Config.jumpMinSeconds + "-" + Config.jumpMaxSeconds + " seconds."
                : "Fake players stopped jumping and stand still."), true);
        return 1;
    }

    private static int jumpOnce(CommandContext<CommandSourceStack> c) {
        List<FakePlayer> list = bots(c.getSource().getServer());
        for (FakePlayer f : list) f.jumpNow();
        return list.size();
    }

    private static int remove(CommandContext<CommandSourceStack> c, String name) {
        for (FakePlayer f : bots(c.getSource().getServer())) {
            if (f.getGameProfile().name().equalsIgnoreCase(name)) {
                f.leave(Component.literal("Removed"));
                c.getSource().sendSuccess(() -> Component.literal("Removed " + name + "."), false);
                return 1;
            }
        }
        c.getSource().sendFailure(Component.literal("No fake player named " + name + "."));
        return 0;
    }

    private static int removeAll(CommandContext<CommandSourceStack> c) {
        List<FakePlayer> list = bots(c.getSource().getServer());
        for (FakePlayer f : list) f.leave(Component.literal("Removed"));
        int n = list.size();
        c.getSource().sendSuccess(() -> Component.literal("Removed " + n + " fake player(s)."), false);
        return n;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<FakePlayer> list = bots(c.getSource().getServer());
        StringBuilder sb = new StringBuilder();
        for (FakePlayer f : list) sb.append(f.getGameProfile().name()).append(' ');
        c.getSource().sendSuccess(() -> Component.literal(list.size() + " fake player(s): " + sb), false);
        return list.size();
    }
}
