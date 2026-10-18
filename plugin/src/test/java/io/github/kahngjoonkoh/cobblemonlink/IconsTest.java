package io.github.kahngjoonkoh.cobblemonlink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IconsTest {
    /** A grey (TYPE_GRAY) texture on a vanilla-style cube: top, left and right faces lit 1.0, 0.65, 0.40 like the client. */
    @Test
    void cubeIsLitLikeTheClient(@TempDir Path dir) throws Exception {
        Path jar = dir.resolve("client.jar");
        BufferedImage grey = new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) grey.getRaster().setSample(x, y, 0, 200);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(grey, "png", png);
        try (var z = new ZipOutputStream(Files.newOutputStream(jar))) {
            put(z, "assets/minecraft/items/box.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:block/box\"}}");
            put(z, "assets/minecraft/models/block/box.json", """
                    {"display":{"gui":{"rotation":[30,225,0],"scale":[0.625,0.625,0.625]}},"textures":{"all":"block/box"},
                     "elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{"up":{"texture":"#all"},"north":{"texture":"#all"},
                     "east":{"texture":"#all"},"south":{"texture":"#all"},"west":{"texture":"#all"},"down":{"texture":"#all"}}}]}""");
            z.putNextEntry(new ZipEntry("assets/minecraft/textures/block/box.png"));
            z.write(png.toByteArray());
        }
        Icons ic = Icons.build(jar);
        BufferedImage sheet = ImageIO.read(new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(ic.pack)) {{
            for (ZipEntry e; (e = getNextEntry()) != null; ) if (e.getName().endsWith("icons.png")) break;
        }});
        assertEquals(200, sheet.getRGB(32, 14) & 255, 2);       // top
        assertEquals(130, sheet.getRGB(20, 40) & 255, 3);       // left (east), 200 * 0.651
        assertEquals(80, sheet.getRGB(44, 40) & 255, 3);        // right (north), 200 * 0.4
    }

    private static void put(ZipOutputStream z, String name, String text) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
