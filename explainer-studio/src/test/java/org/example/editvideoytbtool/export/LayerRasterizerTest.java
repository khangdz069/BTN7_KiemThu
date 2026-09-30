package org.example.editvideoytbtool.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerRasterizerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void rasterizesAnAssetAsASeparateTransparentFullFramePng() throws Exception {
        BufferedImage source = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, new Color(230, 30, 40, 255).getRGB());
            }
        }
        Path sourceFile = tempDirectory.resolve("source.png");
        ImageIO.write(source, "png", sourceFile.toFile());
        Path resultFile = tempDirectory.resolve("layer.png");

        ExportPlan.LayerItem layer = new ExportPlan.LayerItem(
                "character-1",
                ExportPlan.LayerKind.CHARACTER,
                sourceFile,
                "",
                "Arial",
                48,
                "#111111",
                30,
                20,
                80,
                80,
                0,
                1,
                0,
                0,
                0,
                1
        );
        new LayerRasterizer().rasterize(layer, 160, 100, resultFile);

        BufferedImage result = ImageIO.read(resultFile.toFile());
        assertEquals(160, result.getWidth());
        assertEquals(100, result.getHeight());
        assertEquals(0, new Color(result.getRGB(0, 0), true).getAlpha());
        assertTrue(new Color(result.getRGB(70, 50), true).getAlpha() > 240);
    }

    @Test
    void usesConfiguredTextColor() throws Exception {
        Path resultFile = tempDirectory.resolve("text.png");
        ExportPlan.LayerItem layer = new ExportPlan.LayerItem(
                "text-1",
                ExportPlan.LayerKind.TEXT,
                null,
                "Xin chào",
                "Arial",
                48,
                "#146EB4",
                5,
                5,
                250,
                80,
                0,
                1,
                0,
                0,
                0,
                1
        );
        new LayerRasterizer().rasterize(layer, 300, 100, resultFile);

        BufferedImage result = ImageIO.read(resultFile.toFile());
        boolean foundBluePixel = false;
        for (int y = 0; y < result.getHeight() && !foundBluePixel; y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                Color pixel = new Color(result.getRGB(x, y), true);
                if (pixel.getAlpha() > 100 && pixel.getBlue() > pixel.getRed()) {
                    foundBluePixel = true;
                    break;
                }
            }
        }
        assertTrue(foundBluePixel);
    }
}
