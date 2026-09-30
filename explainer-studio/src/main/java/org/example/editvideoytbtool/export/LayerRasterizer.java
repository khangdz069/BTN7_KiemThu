package org.example.editvideoytbtool.export;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Converts one logical layer into one full-frame transparent PNG. */
final class LayerRasterizer {
    private static final Color DEFAULT_TEXT_COLOR = new Color(25, 28, 34);
    private static final Color PLACEHOLDER_FILL = new Color(244, 246, 248, 235);
    private static final Color PLACEHOLDER_BORDER = new Color(142, 150, 160, 230);

    Path rasterize(ExportPlan.LayerItem layer, int canvasWidth, int canvasHeight, Path target)
            throws ExportException {
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            throw new ExportException(ExportException.Code.INVALID_PROJECT, "Kích thước video không hợp lệ.");
        }

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            configure(graphics);
            graphics.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER,
                    (float) Math.max(0, Math.min(1, layer.opacity()))
            ));

            AffineTransform originalTransform = graphics.getTransform();
            double centerX = layer.x() + layer.width() / 2.0;
            double centerY = layer.y() + layer.height() / 2.0;
            graphics.rotate(Math.toRadians(layer.rotationDegrees()), centerX, centerY);
            switch (layer.kind()) {
                case CHARACTER, IMAGE -> drawAsset(graphics, layer);
                case TEXT -> drawTextLayer(graphics, layer, false);
                case PLACEHOLDER -> drawPlaceholder(graphics, layer);
            }
            graphics.setTransform(originalTransform);
        } finally {
            graphics.dispose();
        }

        try {
            Files.createDirectories(target.toAbsolutePath().normalize().getParent());
            if (!ImageIO.write(canvas, "png", target.toFile())) {
                throw new IOException("No PNG ImageIO writer is installed.");
            }
            return target;
        } catch (IOException e) {
            throw new ExportException(
                    ExportException.Code.FILE_IO,
                    "Không thể tạo ảnh trung gian khi xuất video.",
                    e
            );
        }
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }

    private static void drawAsset(Graphics2D graphics, ExportPlan.LayerItem layer) throws ExportException {
        if (layer.asset() == null || !Files.isRegularFile(layer.asset())) {
            throw new ExportException(
                    ExportException.Code.INVALID_PROJECT,
                    "Không tìm thấy ảnh của lớp ‘" + layer.stableName() + "’. Hãy chọn lại ảnh trước khi xuất.",
                    layer.asset() == null ? "Asset path is empty." : layer.asset().toString()
            );
        }

        BufferedImage source;
        try {
            source = ImageIO.read(layer.asset().toFile());
        } catch (IOException e) {
            throw new ExportException(
                    ExportException.Code.FILE_IO,
                    "Không đọc được ảnh ‘" + layer.asset().getFileName() + "’.",
                    e
            );
        }
        if (source == null) {
            throw new ExportException(
                    ExportException.Code.INVALID_PROJECT,
                    "File ‘" + layer.asset().getFileName() + "’ không phải định dạng ảnh được hỗ trợ."
            );
        }

        int boxWidth = Math.max(1, (int) Math.round(layer.width()));
        int boxHeight = Math.max(1, (int) Math.round(layer.height()));
        double scale = Math.min((double) boxWidth / source.getWidth(), (double) boxHeight / source.getHeight());
        int renderWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int renderHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int renderX = (int) Math.round(layer.x() + (boxWidth - renderWidth) / 2.0);
        int renderY = (int) Math.round(layer.y() + (boxHeight - renderHeight) / 2.0);

        Image scaled = source.getScaledInstance(renderWidth, renderHeight, Image.SCALE_SMOOTH);
        graphics.drawImage(scaled, renderX, renderY, renderWidth, renderHeight, null);
    }

    private static void drawPlaceholder(Graphics2D graphics, ExportPlan.LayerItem layer) {
        float strokeWidth = Math.max(2f, Math.min(6f, (float) (Math.min(layer.width(), layer.height()) / 80.0)));
        Shape box = new RoundRectangle2D.Double(
                layer.x(), layer.y(), layer.width(), layer.height(), 28, 28
        );
        graphics.setColor(PLACEHOLDER_FILL);
        graphics.fill(box);
        graphics.setColor(PLACEHOLDER_BORDER);
        graphics.setStroke(new BasicStroke(
                strokeWidth,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
                10f,
                new float[]{12f, 10f},
                0
        ));
        graphics.draw(box);

        String description = layer.text().isBlank() ? "Chọn ảnh minh họa" : layer.text();
        drawCenteredText(graphics, "ẢNH MINH HỌA\n" + description, layer, new Color(78, 86, 96));
    }

    private static void drawTextLayer(Graphics2D graphics, ExportPlan.LayerItem layer, boolean centered) {
        if (layer.text().isBlank()) {
            return;
        }
        if (centered) {
            drawCenteredText(graphics, layer.text(), layer, parseColor(layer.textColor(), DEFAULT_TEXT_COLOR));
            return;
        }

        int fontSize = layer.fontSize() > 0
                ? Math.max(1, (int) Math.round(layer.fontSize()))
                : bestFontSize(layer, 18, 86, 0.58);
        Font font = new Font(layer.fontFamily(), Font.BOLD, fontSize);
        graphics.setFont(font);
        graphics.setColor(parseColor(layer.textColor(), DEFAULT_TEXT_COLOR));
        FontMetrics metrics = graphics.getFontMetrics(font);
        List<String> lines = wrap(layer.text(), metrics, Math.max(1, (int) layer.width()));
        int lineHeight = Math.max(1, (int) Math.round(metrics.getHeight() * 1.08));
        int x = (int) Math.round(layer.x());
        int baseline = (int) Math.round(layer.y()) + metrics.getAscent();
        int maxY = (int) Math.round(layer.y() + layer.height());
        for (String line : lines) {
            if (baseline + metrics.getDescent() > maxY) {
                break;
            }
            graphics.drawString(line, x, baseline);
            baseline += lineHeight;
        }
    }

    private static void drawCenteredText(
            Graphics2D graphics,
            String text,
            ExportPlan.LayerItem layer,
            Color color
    ) {
        int fontSize = bestFontSize(layer, 16, 48, 0.34);
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
        graphics.setFont(font);
        graphics.setColor(color);
        FontMetrics metrics = graphics.getFontMetrics(font);
        int horizontalPadding = Math.max(16, (int) Math.round(layer.width() * 0.08));
        List<String> lines = wrap(text, metrics, Math.max(1, (int) layer.width() - horizontalPadding * 2));
        int lineHeight = (int) Math.round(metrics.getHeight() * 1.12);
        int totalHeight = lines.size() * lineHeight;
        int baseline = (int) Math.round(layer.y() + (layer.height() - totalHeight) / 2.0) + metrics.getAscent();
        int maxY = (int) Math.round(layer.y() + layer.height());
        for (String line : lines) {
            if (baseline + metrics.getDescent() > maxY) {
                break;
            }
            int x = (int) Math.round(layer.x() + (layer.width() - metrics.stringWidth(line)) / 2.0);
            graphics.drawString(line, x, baseline);
            baseline += lineHeight;
        }
    }

    private static int bestFontSize(ExportPlan.LayerItem layer, int min, int max, double heightRatio) {
        return Math.max(min, Math.min(max, (int) Math.round(layer.height() * heightRatio)));
    }

    private static Color parseColor(String value, Color fallback) {
        if (value == null) {
            return fallback;
        }
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            if (hex.length() == 6) {
                return new Color(Integer.parseInt(hex, 16));
            }
            if (hex.length() == 8) {
                long rgba = Long.parseLong(hex, 16);
                return new Color(
                        (int) ((rgba >> 24) & 0xff),
                        (int) ((rgba >> 16) & 0xff),
                        (int) ((rgba >> 8) & 0xff),
                        (int) (rgba & 0xff)
                );
            }
        } catch (NumberFormatException ignored) {
            // Keep the stable default if an old project contains an invalid color.
        }
        return fallback;
    }

    private static List<String> wrap(String text, FontMetrics metrics, int maxWidth) {
        List<String> result = new ArrayList<>();
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        for (String paragraph : normalized.split("\n", -1)) {
            if (paragraph.isBlank()) {
                result.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && metrics.stringWidth(candidate) > maxWidth) {
                    result.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                } else {
                    if (!current.isEmpty()) {
                        current.append(' ');
                    }
                    current.append(word);
                }
            }
            if (!current.isEmpty()) {
                result.add(current.toString());
            }
        }
        return result;
    }
}
