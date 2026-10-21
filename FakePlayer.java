package com.fakeplayermod;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("EntityConstructor")
public class FakePlayer extends ServerPlayer {
    private final MinecraftServer srv;
    private FakeConnection fakeConn;
    private int jumpCooldown;
    private Rank rank = Rank.NORMAL;
    private Vec3 walkTarget;
    private float walkYaw;
    private int walkTicks;
    private UUID followId;
    private double followDist = 3.0;
    private int marchTicks;
    private int battleTimer;
    private float battlePitch = Float.NaN;
    private Vec3 swayHome;
    private int swayTimer;
    private boolean swayMoving;
    private boolean swayActive;

    private FakePlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        super(server, level, profile, ClientInformation.createDefault());
        this.srv = server;
        resetJumpCooldown();
    }

    public static FakePlayer spawn(MinecraftServer server, ServerLevel level, String name, Vec3 pos, float yaw, Rank rank) {
        GameProfile profile = new GameProfile(FakeIds.forName(name), name);
        FakePlayer p = new FakePlayer(server, level, profile);
        p.rank = rank;
        p.fakeConn = new FakeConnection(PacketFlow.SERVERBOUND);
        server.getPlayerList().placeNewPlayer(p.fakeConn, p,
                new CommonListenerCookie(profile, 0, p.clientInformation(), false));
        p.stopRiding();
        p.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yaw, 0.0F, true);
        p.setHealth(20.0F);
        p.unsetRemoved();
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        p.setYHeadRot(yaw);
        Gear.equip(p, server, rank);
        server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(p, (byte) (yaw * 256 / 360)), level.dimension());
        server.getPlayerList().broadcastAll(ClientboundEntityPositionSyncPacket.of(p), level.dimension());
        return p;
    }

    private void resetJumpCooldown() {
        int min = Config.jumpMinSeconds * 20;
        int max = Math.max(min, Config.jumpMaxSeconds * 20);
        jumpCooldown = ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /** Called when jumping gets switched on so every bot starts its own 3-5 s timer. */
    public void restartJumpTimer() {
        resetJumpCooldown();
    }

    public void jumpNow() {
        if (onGround()) jumpFromGround();
    }

    public Rank getRank() {
        return rank;
    }

    /** Snap body, head and view to a yaw (0 = south, 90 = west, 180 = north, 270 = east), looking straight ahead. */
    public void faceYaw(float yaw) {
        faceYawPitch(yaw, 0.0F);
    }

    public void faceYawPitch(float yaw, float pitch) {
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setXRot(pitch);
        this.xRotO = pitch;
        this.yBodyRot = yaw;
        this.yBodyRotO = yaw;
        this.setYHeadRot(yaw);
        this.yHeadRotO = yaw;
    }

    /** Turn (once) so the eyes point at a spot, looking up or down as needed. */
    public void lookAt(Vec3 target) {
        Vec3 eye = this.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(-dx, dz) * 57.29577951308232);
        float pitch = (float) (-(Mth.atan2(dy, horiz) * 57.29577951308232));
        faceYawPitch(yaw, pitch);
    }

    public void follow(UUID playerId) {
        this.followId = playerId;
        this.followDist = 2.5 + ThreadLocalRandom.current().nextDouble() * 2.5;
    }

    /** Body-move mode: the whole body shuffles around its spot (no looking around, facing never changes). */
    private void swayStep() {
        if (!FakePlayerMod.headMoving || walkTarget != null || followId != null || marchTicks > 0) {
            if (swayActive) {
                swayActive = false;
                swayMoving = false;
                this.xxa = 0.0F;
            }
            return;
        }
        swayActive = true;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (swayHome == null) swayHome = position();
        if (--swayTimer > 0) return;
        if (swayMoving) {
            swayMoving = false;
            this.zza = 0.0F;
            this.xxa = 0.0F;
            swayTimer = 8 + r.nextInt(18);
            return;
        }
        swayMoving = true;
        swayTimer = 3 + r.nextInt(4);
        double yaw = Math.toRadians(getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw); // forward
        double lx = Math.cos(yaw), lz = Math.sin(yaw);  // left
        double dx = swayHome.x - getX(), dz = swayHome.z - getZ();
        if (Math.sqrt(dx * dx + dz * dz) > 1.2) {
            this.zza = (float) Math.signum(dx * fx + dz * fz);
            this.xxa = (float) Math.signum(dx * lx + dz * lz);
        } else {
            this.zza = r.nextInt(3) - 1;
            this.xxa = r.nextInt(3) - 1;
        }
    }

    public void startSway() {
        swayHome = null;
        swayTimer = 0;
        swayMoving = false;
    }

    public void stopSway() {
        swayHome = null;
        swayActive = false;
        swayMoving = false;
        this.xxa = 0.0F;
        this.zza = 0.0F;
    }

    /** March: walk straight ahead (the way the bot faces) for a number of ticks. 0 = stop. */
    public void startMarch(int ticks) {
        stopMoving();
        this.marchTicks = ticks;
        if (ticks <= 0) {
            this.zza = 0.0F;
            this.xxa = 0.0F;
        }
    }

    private void marchStep() {
        if (marchTicks <= 0) return;
        marchTicks--;
        this.zza = 1.0F;
        if (this.horizontalCollision && onGround()) jumpFromGround();
        if (marchTicks == 0) this.zza = 0.0F;
    }

    /** Battle mode: look up a bit and keep swinging at the air. */
    public void startBattle() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        battlePitch = -(12.0F + r.nextFloat() * 14.0F);
        battleTimer = r.nextInt(10);
        faceYawPitch(getYRot(), battlePitch);
    }

    public void stopBattle() {
        battlePitch = Float.NaN;
        faceYawPitch(getYRot(), 0.0F);
    }

    private void battleStep() {
        if (!FakePlayerMod.battling) return;
        if (Float.isNaN(battlePitch)) startBattle();
        this.setXRot(battlePitch);
        if (--battleTimer <= 0) {
            battleTimer = 6 + ThreadLocalRandom.current().nextInt(10);
            this.swing(InteractionHand.MAIN_HAND);
        }
    }

    public void stopMoving() {
        this.followId = null;
        this.walkTarget = null;
        this.marchTicks = 0;
        this.zza = 0.0F;
        this.setSprinting(false);
    }

    private void followStep() {
        if (followId == null || walkTarget != null) return;
        ServerPlayer target = srv.getPlayerList().getPlayer(followId);
        if (target == null) {
            followId = null;
            this.zza = 0.0F;
            return;
        }
        if (target.level() != this.level()) {
            this.teleportTo(target.level(), target.getX(), target.getY(), target.getZ(), Set.of(), getYRot(), 0.0F, true);
            return;
        }
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 40.0) {
            this.teleportTo(this.level(), target.getX(), target.getY(), target.getZ(), Set.of(), getYRot(), 0.0F, true);
            return;
        }
        lookAt(target.getEyePosition());
        if (dist > followDist) {
            this.zza = 1.0F;
            this.setSprinting(dist > 8.0);
            if (this.horizontalCollision && onGround()) jumpFromGround();
        } else {
            this.zza = 0.0F;
            this.setSprinting(false);
        }
    }

    /** Walk to a spot, then turn to finalYaw and stand still. */
    public void walkTo(Vec3 target, float finalYaw) {
        this.walkTarget = target;
        this.walkYaw = finalYaw;
        this.walkTicks = 0;
    }

    private void walkStep() {
        if (walkTarget == null) return;
        double dx = walkTarget.x - getX();
        double dz = walkTarget.z - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        walkTicks++;
        if (dist < 0.4 || walkTicks > 400) {
            if (dist >= 0.4) {
                this.teleportTo(this.level(), walkTarget.x, walkTarget.y, walkTarget.z, Set.of(), walkYaw, 0.0F, true);
            }
            walkTarget = null;
            this.zza = 0.0F;
            faceYaw(walkYaw);
            return;
        }
        faceYaw((float) (Mth.atan2(-dx, dz) * 57.29577951308232));
        this.zza = 1.0F;
        if (this.horizontalCollision && onGround()) jumpFromGround();
    }

    @Override
    public void tick() {
        walkStep();
        followStep();
        marchStep();
        if (srv.getTickCount() % 10 == 0) {
            this.connection.resetPosition();
            this.level().getChunkSource().move(this);
        }
        try {
            super.tick();
            this.doTick();
        } catch (NullPointerException ignored) {
        }
        if (fakeConn != null) fakeConn.drain();
        swayStep();
        battleStep();

        if (FakePlayerMod.jumping) {
            if (jumpCooldown > 0) jumpCooldown--;
            if (jumpCooldown <= 0 && onGround()) {
                jumpFromGround();
                resetJumpCooldown();
            }
        }
    }

    /** Remove this bot from the game (next tick, so it is safe from inside entity ticking). */
    public void leave(Component reason) {
        srv.schedule(new TickTask(srv.getTickCount(),
                () -> this.connection.onDisconnect(new DisconnectionDetails(reason))));
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        setHealth(20.0F);
        leave(this.getCombatTracker().getDeathMessage());
    }

    @Override
    public String getIpAddress() {
        return "127.0.0.1";
    }
}
