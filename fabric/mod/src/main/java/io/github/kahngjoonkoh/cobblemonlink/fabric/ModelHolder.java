package io.github.kahngjoonkoh.cobblemonlink.fabric;

import com.mojang.math.MatrixUtil;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A model drawn for vanilla Java clients: an item display per bone, posed by the server from the keyframes (the
 * maths of blockbench-import-library's BbModelImporter), facing the entity's body, at the entity's scale. A loop
 * (idle, walk, battle_idle, faint) runs under one-shot animations (cry, physical, special, recoil), which take the
 * bones they animate. An interaction box redirects clicks to the entity.
 * ponytail: posed every 2 ticks, the client interpolates between; no head look, no blink.
 */
public class ModelHolder extends ElementHolder {
    private final Entity entity;
    private final Models.Model model;
    private final ItemDisplayElement[] displays;
    private final Matrix4f[] world;
    private final InteractionElement hitbox;
    private float scale;
    private String loop = "idle", shot;
    private int age, shotStart, loopStart;
    private java.util.function.BooleanSupplier battling = () -> false;
    private double lastX, lastZ;

    public ModelHolder(Entity entity, Models.Model model, float scale) {
        this.entity = entity;
        this.model = model;
        this.scale = scale;
        displays = new ItemDisplayElement[model.bones().length];
        world = new Matrix4f[displays.length];
        for (int i = 0; i < displays.length; i++) {
            world[i] = new Matrix4f();
            var item = model.bones()[i].item();
            if (item == null) continue;
            var d = new ItemDisplayElement(item.copy());
            d.setModelTransformation(ItemDisplayContext.HEAD);
            d.setInterpolationDuration(Math.max(1, Config.vanillaJava().poseEvery()) + 1);
            d.setTeleportDuration(3);
            d.setViewRange(5);
            displays[i] = addElement(d);
        }
        hitbox = addElement(InteractionElement.redirect(entity));
        hitbox.setSize(entity.getBbWidth(), entity.getBbHeight());
        pose(0);
    }

    /** Only vanilla Java clients: Cobblemon players see Cobblemon's model, Bedrock players GeyserModelEngine's. */
    @Override
    public boolean startWatching(ServerGamePacketListenerImpl h) {
        return Clients.java(h) && super.startWatching(h);
    }

    public void setScale(float scale) { this.scale = scale; }

    /** A one-shot animation, if the model has it (true); it replaces the loop on the bones it moves. */
    public boolean play(String name) {
        if (!model.anims().containsKey(name) || !Config.vanillaJava().moveAnimations()) return false;
        shot = name;
        shotStart = age;
        return true;
    }

    private void setLoop(String name) {
        name = model.anims().containsKey(name) ? name : "idle";
        if (!name.equals(loop)) {
            loop = name;
            loopStart = age;
        }
    }

    /** Whether the entity is in a battle: battle_idle while it stands still. */
    public void battling(java.util.function.BooleanSupplier b) { battling = b; }

    @Override
    public void tick() {
        super.tick();
        age++;
        boolean moving = Math.abs(entity.getX() - lastX) + Math.abs(entity.getZ() - lastZ) > 0.01;
        lastX = entity.getX();
        lastZ = entity.getZ();
        setLoop(entity instanceof LivingEntity l && l.isDeadOrDying() ? "faint" : moving ? "walk"
                : battling.getAsBoolean() ? "battle_idle" : "idle");
        int every = Config.vanillaJava().poseEvery(); // 0: a still model, posed once (and turned every second)
        if (every > 0 && age % every == 0 && !getWatchingPlayers().isEmpty()) pose((age - loopStart) / 20f);
        else if (every == 0 && age % 20 == 0) turn();
        else if (shot != null && (age - shotStart) / 20f > model.anims().get(shot).length() && model.anims().get(shot).loop() == 0) shot = null;
        int[] bits = bedrockAnims();
        if (!java.util.Arrays.equals(bits, lastBits)) {
            lastBits = bits;
            bedrockChanged(bits);
        }
        hitbox.setSize(entity.getBbWidth(), entity.getBbHeight());
    }

    private int[] lastBits = new int[0];

    /** GeyserModelEngine's animation bits (its anim0, anim1.. properties): the model's animations sorted by name. */
    public int[] bedrockAnims() {
        var names = new java.util.ArrayList<>(model.anims().keySet());
        java.util.Collections.sort(names);
        int[] bits = new int[(names.size() + 23) / 24];
        for (int i = 0; i < names.size(); i++)
            if (names.get(i).equals(loop) || names.get(i).equals(shot)) bits[i / 24] |= 1 << (i % 24);
        return bits;
    }

    /** Called when the animations Bedrock players should see change. */
    protected void bedrockChanged(int[] anims) {}

    private void pose(float seconds) {
        Models.Anim base = model.anims().get(loop), one = shot == null ? null : model.anims().get(shot);
        float tb = base == null ? 0 : time(base, seconds);
        float to = 0;
        if (one != null) {
            to = (age - shotStart) / 20f;
            if (to > one.length() && one.loop() == 0) {
                shot = null;
                one = null;
            } else to = time(one, to);
        }
        Matrix4f root = new Matrix4f().scale(scale).rotateY((float) Math.PI);
        Vector3f p = new Vector3f(), r = new Vector3f(), s = new Vector3f();
        float yaw = entity instanceof LivingEntity l ? l.yBodyRot : entity.getYRot();
        for (int i = 0; i < displays.length; i++) {
            Models.Bone b = model.bones()[i];
            float[][] ch = one != null ? one.channels().get(i) : null;
            float t = to;
            if (ch == null) {
                ch = base == null ? null : base.channels().get(i);
                t = tb;
            }
            Vector3f ps = ch == null ? null : Models.sample(ch[0], t, p);
            Vector3f rs = ch == null ? null : Models.sample(ch[1], t, r);
            Vector3f ss = ch == null ? null : Models.sample(ch[2], t, s);
            Matrix4f m = world[i].set(b.parent() >= 0 ? world[b.parent()] : root);
            m.translate(ps == null ? b.local() : new Vector3f(-ps.x, ps.y, ps.z).div(16).add(b.local()));
            m.rotate(rs == null ? b.rest() : Models.euler(-rs.x, -rs.y, rs.z).mul(b.rest()));
            if (ss != null) m.scale(ss);
            ItemDisplayElement d = displays[i];
            if (d == null) continue;
            Matrix4f full = new Matrix4f(m).scale(b.fit());
            var svd = MatrixUtil.svdDecompose(new Matrix3f(full));
            d.setTranslation(full.getTranslation(new Vector3f()));
            d.setLeftRotation(svd.getLeft());
            d.setScale(svd.getMiddle());
            d.setRightRotation(svd.getRight());
            d.setYaw(yaw);
            d.startInterpolationIfDirty();
        }
    }

    private void turn() {
        float yaw = entity instanceof LivingEntity l ? l.yBodyRot : entity.getYRot();
        for (ItemDisplayElement d : displays) if (d != null) d.setYaw(yaw);
    }

    private static float time(Models.Anim a, float seconds) {
        if (a.length() <= 0) return 0;
        return a.loop() == 1 ? seconds % a.length() : Math.min(seconds, a.length());
    }
}
