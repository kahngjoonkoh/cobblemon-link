package io.github.kahngjoonkoh.cobblemonlink;

import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Cobblemon's battle screen for Java without a client mod. The camera is held on a still entity while the player sits
 * on another; a passenger's client sends its aim every tick whatever the camera shows (LocalPlayer.sendChanges), so the
 * mouse turns an aim nobody sees, and that aim moves a cursor drawn in the overlay. Tiles sit at Cobblemon's positions,
 * anchored to the bottom left like its GUI, and light up under the cursor. A left click arrives as an arm swing.
 * ponytail: the cursor moves at the server's 20 ticks/s plus ping; the client never draws its own player while the
 * camera is elsewhere, so there is no way to draw it locally.
 */
final class Screen {
    /** top = px from the screen's bottom edge to the tile's top edge (Cobblemon's H - y). */
    record Tile(int x, int top, int w, int h, char idle, char hover, Consumer<Ui.Overlay> label, Runnable action) {
        boolean contains(double cx, double cy) { return cx >= x && cx <= x + w && cy <= top && cy >= top - h; }
    }

    static final double PX_PER_DEG = 3;   // ~ a desktop cursor's speed at GUI scale 3, default sensitivity
    static final int MAX_X = 320, MAX_Y = 240; // every clickable tile is inside this bottom-left box

    private final Player player;
    private final Entity seat, cam;
    private final Hotbar hotbar;
    private List<Tile> tiles = List.of();
    private double cx = 57, cy = 72;       // starts on Fight
    private float yaw, pitch;
    private int lastSwing = -100, ticks;
    private boolean closed;

    private Screen(Player player, Entity seat, Entity cam) {
        this.player = player;
        this.seat = seat;
        this.cam = cam;
        this.hotbar = new Hotbar(player);
    }

    /**
     * Seats the player and fixes the camera on `target` (the opponent; pitch kept within 30 degrees), or returns null
     * if the camera can't be set (then use the dialog).
     */
    static Screen open(CobblemonLink pl, Player p, Location target) {
        Location feet = p.getLocation();
        feet.setPitch(0);
        Location eye = p.getEyeLocation();
        eye.setDirection(target.toVector().subtract(eye.toVector()));
        eye.setPitch(Math.clamp(eye.getPitch(), -30, 30));
        eye.add(eye.getDirection().multiply(0.25)); // just ahead of the head, so it isn't inside the view
        BlockDisplay seat = p.getWorld().spawn(feet, BlockDisplay.class, d -> d.setPersistent(false));
        ItemDisplay cam = p.getWorld().spawn(eye, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
        });
        p.showEntity(pl, cam);
        seat.addPassenger(p);
        // ponytail: hides the first-person arm in front of the fixed camera; others see only the armour meanwhile
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        p.setRotation(feet.getYaw(), 0); // level aim: pitch stops at ±90, so start in the middle
        Screen s = new Screen(p, seat, cam);
        s.yaw = feet.getYaw();
        if (!camera(p, cam)) {
            pl.getLogger().warning("Battle camera unavailable (Paper internals changed?); using the dialog menu");
            s.close();
            return null;
        }
        return s;
    }

    /** ponytail: Paper has no API for ClientboundSetCameraPacket outside spectator mode; reflection on the Mojang names. */
    private static boolean camera(Player p, Entity target) {
        try {
            Object h = p.getClass().getMethod("getHandle").invoke(p);
            Object e = target.getClass().getMethod("getHandle").invoke(target);
            Class<?> ent = Class.forName("net.minecraft.world.entity.Entity");
            Object pkt = Class.forName("net.minecraft.network.protocol.game.ClientboundSetCameraPacket").getConstructor(ent).newInstance(e);
            Object conn = h.getClass().getField("connection").get(h);
            conn.getClass().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet")).invoke(conn, pkt);
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    void show(List<Tile> tiles) { this.tiles = tiles; }

    boolean idle() { return tiles.isEmpty(); }

    /** Every tick: follow the aim. Moves are relative and clamped, so the cursor never drifts from the aim. */
    void tick() {
        ticks++;
        Location l = player.getLocation();
        float dYaw = l.getYaw() - yaw, dPitch = l.getPitch() - pitch;
        dYaw -= 360 * Math.round(dYaw / 360);
        yaw = l.getYaw();
        pitch = l.getPitch();
        cx = Math.clamp(cx + dYaw * PX_PER_DEG, 0, MAX_X);
        cy = Math.clamp(cy - dPitch * PX_PER_DEG, 0, MAX_Y);
    }

    void draw(Ui.Overlay o) {
        hotbar.draw(o); // the client hides its own while the camera is elsewhere
        Tile h = hovered();
        for (Tile t : tiles) {
            o.glyph(Ui.Overlay.BL, t.x, t.top, t == h ? t.hover : t.idle, t.w + 1); // build_ui.py: advance = width + 1
            t.label.accept(o);
        }
        o.glyph(Ui.Overlay.BL, (int) Math.round(cx), (int) Math.round(cy), Ui.CURSOR, 9);
    }

    private Tile hovered() {
        for (Tile t : tiles) if (t.contains(cx, cy)) return t;
        return null;
    }

    /** Arm swing. Held down, the client swings every tick; a click is the first swing after a pause. */
    void swing() {
        boolean click = ticks - lastSwing > 3;
        lastSwing = ticks;
        Tile t = hovered();
        if (click && t != null) t.action.run();
    }

    /** The seat; Shift off it hides the battle (Battle.hide). */
    boolean holds(Entity vehicle) { return !closed && vehicle.equals(seat); }

    void close() {
        closed = true;
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        if (player.isOnline()) camera(player, player);
        seat.eject();
        seat.remove();
        cam.remove();
    }
}
