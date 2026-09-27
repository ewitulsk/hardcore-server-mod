package xyz.hardcoreserver;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Follow camera for dead spectators. The spectator picks a spot and angle around a player, runs /follow, and the
 * view is locked to an invisible item display that the server keeps at the same offset from the player every tick.
 * Uses the vanilla camera packet (same as /spectate), so unmodded clients work; sneaking exits, as in vanilla.
 */
public final class FollowCam {
    public static final String CAMERA_TAG = "hardcoreserver_follow_cam";

    private static final class Session {
        final UUID target;
        final Vec3 offset;       // camera eye position minus target feet position, at the start
        final float yaw, pitch;  // held view angle
        final boolean rotate;    // rotate the offset with the target's body
        final float baseBodyYaw; // target body yaw at the start (for rotate mode)
        Display.ItemDisplay camera;

        Session(UUID target, Vec3 offset, float yaw, float pitch, boolean rotate, float baseBodyYaw) {
            this.target = target;
            this.offset = offset;
            this.yaw = yaw;
            this.pitch = pitch;
            this.rotate = rotate;
            this.baseBodyYaw = baseBodyYaw;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static boolean isFollowing(ServerPlayer spectator) {
        return SESSIONS.containsKey(spectator.getUUID());
    }

    /** Starts following, locking the camera at the spectator's current offset and view angle. */
    public static void start(ServerPlayer spectator, ServerPlayer target, boolean rotate) {
        stop(spectator, null);
        Session s = new Session(target.getUUID(), spectator.getEyePosition().subtract(target.position()),
                spectator.getYRot(), spectator.getXRot(), rotate, target.yBodyRot);
        SESSIONS.put(spectator.getUUID(), s);
        placeCamera(spectator, target, s);
    }

    /** Stops following (if active) and gives the spectator their normal view back. */
    public static void stop(ServerPlayer spectator, Component message) {
        Session s = SESSIONS.remove(spectator.getUUID());
        if (s == null) return;
        if (s.camera != null) {
            if (spectator.getCamera() == s.camera) spectator.setCamera(spectator);
            s.camera.discard();
        }
        if (message != null) spectator.sendSystemMessage(message);
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> e = it.next();
            Session s = e.getValue();
            ServerPlayer spectator = server.getPlayerList().getPlayer(e.getKey());
            String problem = null;
            ServerPlayer target = server.getPlayerList().getPlayer(s.target);
            if (spectator == null) problem = "";
            else if (!HardcoreCommands.isDeadSpectator(spectator)) problem = "";
            else if (s.camera == null || spectator.getCamera() != s.camera) problem = "Stopped following.";
            else if (target == null) problem = "Stopped following: that player left the game.";

            if (problem != null) {
                it.remove();
                if (s.camera != null) {
                    if (spectator != null && spectator.getCamera() == s.camera) spectator.setCamera(spectator);
                    s.camera.discard();
                }
                if (spectator != null && !problem.isEmpty()) {
                    spectator.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.GRAY));
                }
                continue;
            }

            if (target.level() != s.camera.level()) {
                // Target changed dimension: rebuild the camera over there.
                s.camera.discard();
                placeCamera(spectator, target, s);
            } else {
                Vec3 pos = cameraPos(target, s);
                s.camera.snapTo(pos.x, pos.y, pos.z, cameraYaw(target, s), s.pitch);
            }
        }
    }

    private static void placeCamera(ServerPlayer spectator, ServerPlayer target, Session s) {
        ServerLevel level = target.level();
        Display.ItemDisplay cam = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        if (cam == null) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("teleport_duration", 2); // client-side smoothing between ticks
        cam.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        Vec3 pos = cameraPos(target, s);
        cam.snapTo(pos.x, pos.y, pos.z, cameraYaw(target, s), s.pitch);
        cam.addTag(CAMERA_TAG);
        cam.setNoGravity(true);
        level.addFreshEntity(cam); // empty item display = invisible
        s.camera = cam;
        spectator.setCamera(cam);
    }

    private static Vec3 cameraPos(ServerPlayer target, Session s) {
        Vec3 off = s.offset;
        if (s.rotate) {
            double d = Math.toRadians(target.yBodyRot - s.baseBodyYaw);
            double cos = Math.cos(d), sin = Math.sin(d);
            off = new Vec3(off.x * cos - off.z * sin, off.y, off.x * sin + off.z * cos);
        }
        return target.position().add(off);
    }

    private static float cameraYaw(ServerPlayer target, Session s) {
        return s.rotate ? s.yaw + (target.yBodyRot - s.baseBodyYaw) : s.yaw;
    }

    /** Removes every camera (server shutdown). */
    public static void stopAll(MinecraftServer server) {
        for (UUID id : SESSIONS.keySet().toArray(new UUID[0])) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            Session s = SESSIONS.remove(id);
            if (s != null && s.camera != null) {
                if (p != null && p.getCamera() == s.camera) p.setCamera(p);
                s.camera.discard();
            }
        }
    }

    /** Leftover cameras (e.g. after a crash) are removed as soon as they load. */
    public static void onEntityLoad(Entity entity, ServerLevel level) {
        if (!entity.entityTags().contains(CAMERA_TAG)) return;
        for (Session s : SESSIONS.values()) {
            if (s.camera == entity) return;
        }
        entity.discard();
    }

    private FollowCam() {}
}
