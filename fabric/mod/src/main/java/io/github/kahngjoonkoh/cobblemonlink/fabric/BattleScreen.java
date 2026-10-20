package io.github.kahngjoonkoh.cobblemonlink.fabric;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Cobblemon's battle screen for vanilla Java (full level), the Paper version's Screen: the camera held on a still
 * entity while the player sits on another; a passenger's client still sends its aim every tick, so the mouse turns an
 * aim nobody sees and that aim moves a cursor drawn in the overlay. Tiles sit at Cobblemon's positions, anchored to
 * the bottom left; a left click arrives as an arm swing.
 * ponytail: the cursor moves at 20 ticks/s plus ping; no hotbar is redrawn while the camera is elsewhere.
 */
final class BattleScreen {
    record Tile(int x, int top, int w, int h, char idle, char hover, Consumer<Ui.Overlay> label, Runnable action) {
        boolean contains(double cx, double cy) { return cx >= x && cx <= x + w && cy <= top && cy >= top - h; }
    }

    static final double PX_PER_DEG = 3;
    static final int MAX_X = 320, MAX_Y = 240;
    private final ServerPlayer player;
    private final Display.BlockDisplay seat;
    private final Display.ItemDisplay cam;
    private List<Tile> tiles = List.of();
    private double cx = 57, cy = 72;
    private float yaw, pitch;
    private int lastSwing = -100, ticks;
    private boolean closed;

    private BattleScreen(ServerPlayer player, Display.BlockDisplay seat, Display.ItemDisplay cam) {
        this.player = player;
        this.seat = seat;
        this.cam = cam;
    }

    /** Seats the player and holds the camera on target (pitch within 30 degrees). */
    static BattleScreen open(ServerPlayer p, Vec3 target) {
        var level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 dir = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) Math.max(-30, Math.min(30, -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)))));
        Display.BlockDisplay seat = EntityType.BLOCK_DISPLAY.create(level);
        Display.ItemDisplay cam = EntityType.ITEM_DISPLAY.create(level);
        if (seat == null || cam == null) return null;
        seat.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
        Vec3 ahead = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(0.25));
        cam.moveTo(ahead.x, ahead.y, ahead.z, yaw, pitch);
        level.addFreshEntity(seat);
        level.addFreshEntity(cam);
        p.startRiding(seat, true);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false, false)); // no arm in front of the camera
        BattleScreen s = new BattleScreen(p, seat, cam);
        s.yaw = p.getYRot();
        s.pitch = p.getXRot();
        p.connection.send(new ClientboundSetCameraPacket(cam));
        return s;
    }

    void show(List<Tile> tiles) { this.tiles = tiles; }

    boolean idle() { return tiles.isEmpty(); }

    /** Every tick: follow the aim; false once the player has left the seat (Shift: hide the battle). */
    boolean tick() {
        ticks++;
        if (closed || player.getVehicle() != seat) return false;
        float dYaw = player.getYRot() - yaw, dPitch = player.getXRot() - pitch;
        dYaw -= 360 * Math.round(dYaw / 360);
        yaw = player.getYRot();
        pitch = player.getXRot();
        cx = Math.clamp(cx + dYaw * PX_PER_DEG, 0, MAX_X);
        cy = Math.clamp(cy - dPitch * PX_PER_DEG, 0, MAX_Y);
        return true;
    }

    void draw(Ui.Overlay o) {
        Tile h = hovered();
        for (Tile t : tiles) {
            o.glyph(Ui.Overlay.BL, t.x, t.top, t == h ? t.hover : t.idle, t.w + 1);
            t.label.accept(o);
        }
        o.glyph(Ui.Overlay.BL, (int) Math.round(cx), (int) Math.round(cy), Ui.CURSOR, 9);
    }

    private Tile hovered() {
        for (Tile t : tiles) if (t.contains(cx, cy)) return t;
        return null;
    }

    /** Arm swing: a click is the first swing after a pause (held down, the client swings every tick). */
    void swing() {
        boolean click = ticks - lastSwing > 3;
        lastSwing = ticks;
        Tile t = hovered();
        if (click && t != null) t.action.run();
    }

    void close() {
        if (closed) return;
        closed = true;
        player.removeEffect(MobEffects.INVISIBILITY);
        if (!player.hasDisconnected()) player.connection.send(new ClientboundSetCameraPacket(player));
        if (player.getVehicle() == seat) player.stopRiding();
        seat.discard();
        cam.discard();
    }
}
