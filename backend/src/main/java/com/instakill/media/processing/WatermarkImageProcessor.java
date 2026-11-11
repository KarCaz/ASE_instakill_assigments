package com.instakill.media.processing;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

/**
 * Scales an image down to a maximum width (keeping the aspect ratio, never
 * enlarging) and draws a small text watermark in the bottom-right corner.
 *
 * <p>Uses only the JDK's {@code javax.imageio} / Java2D, so there is no extra dependency.
 * GIFs are processed as a single (first) frame, so animation is not kept.
 * WebP is not readable by the JDK and is therefore stored unchanged by the caller.
 */
@Component
public class WatermarkImageProcessor implements ImageProcessor {

    private static final Set<String> SUPPORTED = Set.of("jpg", "jpeg", "png", "gif");

    private final int maxWidth;
    private final String watermarkText;

    public WatermarkImageProcessor(ImageProcessingProperties properties) {
        this.maxWidth = properties.maxWidth();
        this.watermarkText = properties.watermarkText();
    }

    @Override
    public boolean supports(String extension) {
        return extension != null && SUPPORTED.contains(extension);
    }

    @Override
    public byte[] process(byte[] input, String extension) {
        if (!supports(extension)) {
            throw new ImageProcessingException("Unsupported image type: " + extension);
        }
        BufferedImage source = read(input);
        boolean jpeg = extension.equals("jpg") || extension.equals("jpeg");

        BufferedImage result = scaleToMaxWidth(source, jpeg);
        drawWatermark(result);
        return write(result, jpeg ? "jpg" : extension);
    }

    private BufferedImage read(byte[] input) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(input));
            if (image == null) {
                throw new ImageProcessingException("File is not a readable image");
            }
            return image;
        } catch (IOException e) {
            throw new ImageProcessingException("Could not read image", e);
        } catch (RuntimeException e) {
            if (e instanceof ImageProcessingException) {
                throw e;
            }
            throw new ImageProcessingException("Could not read image", e);
        }
    }

    private BufferedImage scaleToMaxWidth(BufferedImage source, boolean opaque) {
        int type = opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        int targetWidth = Math.min(source.getWidth(), maxWidth);
        int targetHeight = Math.max(1, Math.round((float) source.getHeight() * targetWidth / source.getWidth()));

        BufferedImage current = copyAs(source, type);
        // Halve repeatedly while far above the target: much smoother than one big jump.
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2, type);
        }
        if (current.getWidth() != targetWidth || current.getHeight() != targetHeight) {
            current = resize(current, targetWidth, targetHeight, type);
        }
        return current;
    }

    private static BufferedImage copyAs(BufferedImage source, int type) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), type);
        Graphics2D g = copy.createGraphics();
        try {
            if (type == BufferedImage.TYPE_INT_RGB) {
                g.setColor(Color.WHITE); // flatten transparency (JPEG has no alpha)
                g.fillRect(0, 0, copy.getWidth(), copy.getHeight());
            }
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }

    private static BufferedImage resize(BufferedImage image, int width, int height, int type) {
        BufferedImage out = new BufferedImage(width, height, type);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private void drawWatermark(BufferedImage image) {
        if (watermarkText == null || watermarkText.isBlank()) {
            return;
        }
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int fontSize = Math.max(10, image.getWidth() / 20);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            FontMetrics metrics = g.getFontMetrics();

            int margin = Math.max(4, fontSize / 2);
            int x = Math.max(margin / 2, image.getWidth() - metrics.stringWidth(watermarkText) - margin);
            int y = image.getHeight() - margin - metrics.getDescent();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
            g.setColor(Color.BLACK); // 1px shadow keeps the text readable on light images
            g.drawString(watermarkText, x + 1, y + 1);
            g.setColor(Color.WHITE);
            g.drawString(watermarkText, x, y);
        } finally {
            g.dispose();
        }
    }

    private static byte[] write(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, out)) {
                throw new ImageProcessingException("No encoder available for " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new ImageProcessingException("Could not encode image", e);
        }
    }
}
