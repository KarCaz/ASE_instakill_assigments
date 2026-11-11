package com.instakill.media.processing;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatermarkImageProcessorTest {

    private final WatermarkImageProcessor processor =
            new WatermarkImageProcessor(new ImageProcessingProperties(300, "Instakill Demo"));

    @Test
    void largeImageIsScaledToMaxWidthKeepingAspectRatio() throws Exception {
        byte[] out = processor.process(image(1000, 600, "png", Color.BLUE), "png");

        BufferedImage result = decode(out);
        assertThat(result.getWidth()).isEqualTo(300);
        assertThat(result.getHeight()).isEqualTo(180);
    }

    @Test
    void smallImageIsNotEnlarged() throws Exception {
        byte[] out = processor.process(image(120, 80, "png", Color.BLUE), "png");

        BufferedImage result = decode(out);
        assertThat(result.getWidth()).isEqualTo(120);
        assertThat(result.getHeight()).isEqualTo(80);
    }

    @Test
    void watermarkIsDrawnInBottomRightCornerOnly() throws Exception {
        byte[] out = processor.process(image(300, 200, "png", Color.BLACK), "png");
        BufferedImage result = decode(out);

        assertThat(hasBrightPixels(result, 150, 150, 300, 200)).as("bottom-right").isTrue();
        assertThat(hasBrightPixels(result, 0, 0, 150, 100)).as("top-left").isFalse();
    }

    @Test
    void jpegAndGifAreSupported() throws Exception {
        for (String ext : new String[]{"jpg", "jpeg", "gif"}) {
            byte[] out = processor.process(image(800, 400, ext.equals("jpeg") ? "jpg" : ext, Color.RED), ext);
            assertThat(decode(out).getWidth()).as(ext).isEqualTo(300);
        }
    }

    @Test
    void invalidBytesAreRejected() {
        assertThatThrownBy(() -> processor.process("not an image".getBytes(), "png"))
                .isInstanceOf(ImageProcessingException.class);
    }

    @Test
    void supportsOnlyDecodableImageTypes() {
        assertThat(processor.supports("png")).isTrue();
        assertThat(processor.supports("mp4")).isFalse();
        assertThat(processor.supports("webp")).isFalse();
        assertThat(processor.supports(null)).isFalse();
    }

    private static boolean hasBrightPixels(BufferedImage img, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if ((img.getRGB(x, y) & 0xFF) > 150) {
                    return true;
                }
            }
        }
        return false;
    }

    private static byte[] image(int w, int h, String format, Color color) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }
}
