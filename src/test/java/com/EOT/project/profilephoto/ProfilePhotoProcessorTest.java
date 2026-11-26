package com.EOT.project.profilephoto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.EOT.project.exception.BadRequestException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

public class ProfilePhotoProcessorTest {
    private final ProfilePhotoProcessor processor = new ProfilePhotoProcessor();

    public static byte[] image(String format, int width, int height, int type) throws Exception {
        BufferedImage img = new BufferedImage(width, height, type);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    public static String dataUrl(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static BufferedImage decoded(byte[] jpeg) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    @Test
    void producesA256SquareJpegFromALargeLandscapePhoto() throws Exception {
        byte[] result = processor.process(dataUrl("image/jpeg", image("jpg", 1280, 720, BufferedImage.TYPE_INT_RGB)));

        assertThat(result[0] & 0xFF).isEqualTo(0xFF);
        assertThat(result[1] & 0xFF).isEqualTo(0xD8);
        BufferedImage out = decoded(result);
        assertThat(out.getWidth()).isEqualTo(256);
        assertThat(out.getHeight()).isEqualTo(256);
        assertThat(result.length).isLessThan(60_000);
    }

    @Test
    void cropsTheCenterSoFacesAreNotStretched() throws Exception {
        // 400x200: left half red, right half blue. The centered 200x200 square keeps both halves.
        BufferedImage out = decoded(processor.process(dataUrl("image/png", image("png", 400, 200, BufferedImage.TYPE_INT_RGB))));
        Color left = new Color(out.getRGB(20, 128));
        Color right = new Color(out.getRGB(235, 128));
        assertThat(left.getRed()).isGreaterThan(150);
        assertThat(left.getBlue()).isLessThan(100);
        assertThat(right.getBlue()).isGreaterThan(150);
        assertThat(right.getRed()).isLessThan(100);
    }

    @Test
    void acceptsBareBase64AndPngWithTransparency() throws Exception {
        byte[] png = image("png", 300, 300, BufferedImage.TYPE_INT_ARGB);
        byte[] result = processor.process(Base64.getEncoder().encodeToString(png));
        assertThat(decoded(result).getWidth()).isEqualTo(256);
    }

    @Test
    void neverKeepsMetadataFromTheOriginal() throws Exception {
        byte[] jpeg = image("jpg", 300, 300, BufferedImage.TYPE_INT_RGB);
        byte[] marker = "Exif\0\0GPS-LAT-SECRET".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(jpeg, 0, 2);                                   // SOI
        int length = marker.length + 2;
        withExif.write(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
        withExif.write(marker);
        withExif.write(jpeg, 2, jpeg.length - 2);

        byte[] result = processor.process(dataUrl("image/jpeg", withExif.toByteArray()));

        assertThat(new String(result, StandardCharsets.ISO_8859_1)).doesNotContain("GPS-LAT-SECRET").doesNotContain("Exif");
    }

    @Test
    void rejectsWhatIsNotAJpegOrPng() {
        assertThatThrownBy(() -> processor.process(dataUrl("image/jpeg", "not an image at all".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Formato");
        assertThatThrownBy(() -> processor.process("data:image/svg+xml;base64,PHN2Zy8+"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> processor.process("data:text/html;base64,PGgxPg=="))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsBrokenOrEmptyInput() {
        assertThatThrownBy(() -> processor.process(null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> processor.process("   ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> processor.process("%%%not-base64%%%")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsATruncatedImage() throws Exception {
        byte[] jpeg = image("jpg", 400, 400, BufferedImage.TYPE_INT_RGB);
        byte[] cut = java.util.Arrays.copyOf(jpeg, 40);
        assertThatThrownBy(() -> processor.process(dataUrl("image/jpeg", cut))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsPicturesThatAreTooSmallOrTooLarge() throws Exception {
        assertThatThrownBy(() -> processor.process(dataUrl("image/png", image("png", 40, 40, BufferedImage.TYPE_INT_RGB))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("pequeña");
        byte[] huge = image("png", 5000, 100, BufferedImage.TYPE_BYTE_GRAY);
        assertThatThrownBy(() -> processor.process(dataUrl("image/png", huge)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("grande");
    }

    @Test
    void rejectsFilesOverTheSizeLimit() {
        byte[] big = new byte[ProfilePhotoProcessor.MAX_BYTES + 1];
        big[0] = (byte) 0xFF; big[1] = (byte) 0xD8; big[2] = (byte) 0xFF;
        assertThatThrownBy(() -> processor.process(dataUrl("image/jpeg", big)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("grande");
    }
}
