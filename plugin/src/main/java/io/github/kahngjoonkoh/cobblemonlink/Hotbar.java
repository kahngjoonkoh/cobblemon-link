package io.github.kahngjoonkoh.cobblemonlink;

import java.util.Map;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;

/**
 * The vanilla hotbar and health area, drawn in the battle overlay: the client leaves them out while the camera is on
 * another entity (Hud.getCameraPlayer gates extractItemHotbar and extractPlayerHealth; everything else in the HUD
 * still draws itself). A line-for-line port of those two methods from the 26.3 client, so heart types, blinking,
 * shaking, absorption, armour, food and air follow the client's own rules; re-check them when Minecraft updates.
 * Sprites are the client's own (Icons.hud); x is from the screen's centre, y from its bottom edge to the sprite's top.
 * ponytail: per server tick (the client animates per frame, but its state also changes per tick); a picture only,
 * the scroll wheel still moves the selection; no attack indicator (off by default), durability bars or glint.
 */
final class Hotbar {
    static final int BC = 2 | 8, ITEM = BC | 16; // Ui.Overlay corner: bottom centre; bit 16 = doubled (8 px sprite -> 16)
    static Map<String, Component> icons = Map.of();     // Icons.java, set at startup
    static Map<String, Icons.Sprite> hud = Map.of();

    private final Player player;
    private final Random random = new Random(); // LegacyRandomSource is java.util.Random's generator: the same shakes
    private int lastHealth, displayHealth, lastBubble;
    private long lastHealthTime, healthBlinkTime;

    Hotbar(Player player) {
        this.player = player;
        lastHealth = displayHealth = (int) Math.ceil(player.getHealth());
    }

    void draw(Ui.Overlay o) {
        if (hud.isEmpty()) return;
        long tick = Bukkit.getCurrentTick();
        itemHotbar(o);
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) playerHealth(o, tick); // gameMode.canHurtPlayer()
    }

    /** Hud.extractItemHotbar */
    private void itemHotbar(Ui.Overlay o) {
        PlayerInventory inv = player.getInventory();
        ItemStack offhand = inv.getItemInOffHand();
        boolean offLeft = player.getMainHand() == MainHand.RIGHT;
        sprite(o, "hotbar", -91, 22);
        sprite(o, "hotbar_selection", -91 - 1 + inv.getHeldItemSlot() * 20, 23);
        if (!offhand.isEmpty()) sprite(o, offLeft ? "hotbar_offhand_left" : "hotbar_offhand_right", offLeft ? -91 - 29 : 91, 23);
        for (int i = 0; i < 9; i++) slot(o, -90 + i * 20 + 2, inv.getItem(i));
        if (!offhand.isEmpty()) slot(o, offLeft ? -91 - 26 : 91 + 10, offhand);
    }

    /** Hud.extractSlot, without the durability bar: the icon, and the count bottom-right with its shadow. */
    private static void slot(Ui.Overlay o, int x, ItemStack it) {
        if (it == null || it.isEmpty()) return;
        Component icon = CobblemonLink.BALL_MODEL.equals(it.getItemMeta().getItemModel())
                ? Ui.glyphs("\ue270") : icons.get(it.getType().getKey().getKey()); // build_ui.py: the Poké Ball
        if (icon instanceof TextComponent) o.add(BC, x, 19, icon, 17); // rendered icon (Icons.pack), 16 px + 1
        else if (icon != null) o.add(ITEM, x, 19, icon, 8);            // the client's flat sprite
        if (it.getAmount() > 1) {
            String n = String.valueOf(it.getAmount());
            int tx = x + 19 - 2 - Ui.width(n);
            o.text(BC + 4, tx + 1, 9, n);
            o.text(BC, tx, 10, n);
        }
    }

    /** Hud.extractPlayerHealth; y is the client's (guiHeight - y), so its "yo + 1" (lower) is "y - 1" here. */
    private void playerHealth(Ui.Overlay o, long tickCount) {
        int currentHealth = (int) Math.ceil(player.getHealth());
        boolean blink = healthBlinkTime > tickCount && (healthBlinkTime - tickCount) / 3L % 2L == 1L;
        long time = tickCount * 50; // Util.getMillis(), in server ticks
        boolean hurtCooldown = player.getNoDamageTicks() > 0; // damageCooldownTime
        if (currentHealth < lastHealth && hurtCooldown) {
            lastHealthTime = time;
            healthBlinkTime = tickCount + 20;
        } else if (currentHealth > lastHealth && hurtCooldown) {
            lastHealthTime = time;
            healthBlinkTime = tickCount + 10;
        }
        if (time - lastHealthTime > 1000L) {
            displayHealth = currentHealth;
            lastHealthTime = time;
        }
        lastHealth = currentHealth;
        int oldHealth = displayHealth;
        random.setSeed(tickCount * 312871);
        int xLeft = -91, xRight = 91, yLineBase = 39;
        float maxHealth = Math.max((float) player.getAttribute(Attribute.MAX_HEALTH).getValue(), Math.max(oldHealth, currentHealth));
        int totalAbsorption = (int) Math.ceil(player.getAbsorptionAmount());
        int numHealthRows = (int) Math.ceil((maxHealth + totalAbsorption) / 2.0F / 10.0F);
        int healthRowHeight = Math.max(10 - (numHealthRows - 2), 3);
        int yLineAir = yLineBase + 10;
        int heartOffsetIndex = -1;
        if (player.hasPotionEffect(PotionEffectType.REGENERATION)) heartOffsetIndex = (int) (tickCount % (int) Math.ceil(maxHealth + 5.0F));

        armor(o, yLineBase, numHealthRows, healthRowHeight, xLeft);
        hearts(o, xLeft, yLineBase, healthRowHeight, heartOffsetIndex, maxHealth, currentHealth, oldHealth, totalAbsorption, blink);
        food(o, yLineBase, xRight, tickCount); // no vehicle hearts: the battle seat isn't a living entity
        yLineAir += 10;
        air(o, yLineAir, xRight, tickCount);
    }

    private void armor(Ui.Overlay o, int yLineBase, int numHealthRows, int healthRowHeight, int xLeft) {
        int armor = (int) Math.floor(player.getAttribute(Attribute.ARMOR).getValue());
        if (armor <= 0) return;
        int yLineArmor = yLineBase + (numHealthRows - 1) * healthRowHeight + 10;
        for (int i = 0; i < 10; i++) {
            int xo = xLeft + i * 8;
            if (i * 2 + 1 < armor) sprite(o, "armor_full", xo, yLineArmor);
            if (i * 2 + 1 == armor) sprite(o, "armor_half", xo, yLineArmor);
            if (i * 2 + 1 > armor) sprite(o, "armor_empty", xo, yLineArmor);
        }
    }

    private void hearts(Ui.Overlay o, int xLeft, int yLineBase, int healthRowHeight, int heartOffsetIndex, float maxHealth,
                        int currentHealth, int oldHealth, int absorption, boolean blink) {
        String type = player.hasPotionEffect(PotionEffectType.POISON) ? "poisoned_"   // HeartType.forPlayer
                : player.hasPotionEffect(PotionEffectType.WITHER) ? "withered_"
                : player.isFrozen() ? "frozen_" : "";
        boolean hardcore = player.getWorld().isHardcore();
        int healthContainerCount = (int) Math.ceil(maxHealth / 2.0);
        int absorptionContainerCount = (int) Math.ceil(absorption / 2.0);
        int maxHealthHalvesCount = healthContainerCount * 2;
        for (int containerIndex = healthContainerCount + absorptionContainerCount - 1; containerIndex >= 0; containerIndex--) {
            int row = containerIndex / 10, column = containerIndex % 10;
            int xo = xLeft + column * 8, yo = yLineBase + row * healthRowHeight;
            if (currentHealth + absorption <= 4) yo -= random.nextInt(2);
            if (containerIndex < healthContainerCount && containerIndex == heartOffsetIndex) yo += 2;
            heart(o, "container", xo, yo, hardcore, blink, false);
            int halves = containerIndex * 2;
            if (containerIndex >= healthContainerCount) {
                int absorptionHalves = halves - maxHealthHalvesCount;
                if (absorptionHalves < absorption)
                    heart(o, type.equals("withered_") ? type : "absorbing_", xo, yo, hardcore, false, absorptionHalves + 1 == absorption);
            }
            if (blink && halves < oldHealth) heart(o, type, xo, yo, hardcore, true, halves + 1 == oldHealth);
            if (halves < currentHealth) heart(o, type, xo, yo, hardcore, false, halves + 1 == currentHealth);
        }
    }

    /** HeartType.getSprite: [type][hardcore_](full|half)[_blinking]; the container is container[_hardcore][_blinking]. */
    private static void heart(Ui.Overlay o, String type, int x, int y, boolean hardcore, boolean blinks, boolean half) {
        String name = type.equals("container") ? "container" + (hardcore ? "_hardcore" : "")
                : type + (hardcore ? "hardcore_" : "") + (half ? "half" : "full");
        sprite(o, "heart/" + name + (blinks ? "_blinking" : ""), x, y);
    }

    private void food(Ui.Overlay o, int yLineBase, int xRight, long tickCount) {
        int food = player.getFoodLevel();
        String suffix = player.hasPotionEffect(PotionEffectType.HUNGER) ? "_hunger" : "";
        for (int i = 0; i < 10; i++) {
            int yo = yLineBase;
            if (player.getSaturation() <= 0.0F && tickCount % (food * 3 + 1) == 0) yo = yLineBase - (random.nextInt(3) - 1);
            int xo = xRight - i * 8 - 9;
            sprite(o, "food_empty" + suffix, xo, yo);
            if (i * 2 + 1 < food) sprite(o, "food_full" + suffix, xo, yo);
            if (i * 2 + 1 == food) sprite(o, "food_half" + suffix, xo, yo);
        }
    }

    /** Hud.extractAirBubbles, with the bubble pop sound the client would have played. */
    private void air(Ui.Overlay o, int yLineAir, int xRight, long tickCount) {
        int max = player.getMaximumAir(), current = Math.clamp(player.getRemainingAir(), 0, max);
        boolean underWater = player.isUnderWater();
        if (!underWater && current >= max) return;
        yLineAir -= 10; // getAirBubbleYLine: no vehicle heart rows, so (0 - 1) rows lower
        int full = bubble(current, max, -2), popping = bubble(current, max, 0);
        int empty = 10 - bubble(current, max, current != 0 && underWater ? 1 : 0);
        boolean isPopping = full != popping;
        if (!underWater) lastBubble = 0;
        for (int b = 1; b <= 10; b++) {
            int x = xRight - (b - 1) * 8 - 9;
            if (b <= full) sprite(o, "air", x, yLineAir);
            else if (isPopping && b == popping && underWater) {
                sprite(o, "air_bursting", x, yLineAir);
                if (lastBubble != b) {
                    player.playSound(player, "minecraft:ui.hud.bubble_pop", 0.5F + 0.1F * Math.max(0, empty - 3 + 1),
                            1.0F + 0.1F * Math.max(0, empty - 5 + 1));
                    lastBubble = b;
                }
            } else if (b > 10 - empty) {
                int wobble = empty == 10 && tickCount % 2 == 0 ? random.nextInt(2) : 0;
                sprite(o, "air_empty", x, yLineAir - wobble);
            }
        }
    }

    private static int bubble(int current, int max, int offset) { return (int) Math.ceil((float) ((current + offset) * 10) / max); }

    private static void sprite(Ui.Overlay o, String name, int x, int top) {
        Icons.Sprite s = hud.get(name);
        if (s != null) o.add(BC, x, top, s.glyph(), s.advance());
    }
}
