package com.EOT.project.profilephoto;

import com.EOT.project.exception.BadRequestException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Turns whatever the app sends into the one image the server keeps: a 256x256 square JPEG. The
 * picture is decoded and drawn again, so metadata (EXIF, GPS position, camera model) never survives,
 * and anything that is not a real, reasonably sized JPEG or PNG is rejected.
 */
@Component
public class ProfilePhotoProcessor {
    public static final int SIZE = 256;
    static final int MAX_BYTES = 524_288;
    static final int MAX_SIDE = 4096;
    static final int MIN_SIDE = 64;
    static final long MAX_PIXELS = 16_000_000L;
    private static final float QUALITY = 0.85f;
    private static final Pattern DATA_URL = Pattern.compile("^data:image/(?:jpeg|png);base64,(.+)$", Pattern.DOTALL);

    /** @param image a base64 data URL ("data:image/jpeg;base64,...") or the bare base64 text */
    public byte[] process(String image) {
        byte[] bytes = decode(image);
        BufferedImage source = read(bytes);
        return encode(resize(squareCrop(source)));
    }

    private static byte[] decode(String image) {
        String base64 = image == null ? "" : image.trim();
        var match = DATA_URL.matcher(base64);
        if (match.matches()) {
            base64 = match.group(1);
        } else if (base64.startsWith("data:")) {
            throw new BadRequestException("Formato de foto no admitido. Usa JPEG o PNG.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("La foto no es válida.");
        }
        if (bytes.length == 0) {
            throw new BadRequestException("La foto no es válida.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BadRequestException("La foto es demasiado grande.");
        }
        boolean jpeg = bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
        boolean png = bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
        if (!jpeg && !png) {
            throw new BadRequestException("Formato de foto no admitido. Usa JPEG o PNG.");
        }
        return bytes;
    }

    private static BufferedImage read(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BadRequestException("No se pudo leer la foto.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // Checked before decoding, so a tiny file declaring a huge picture cannot exhaust memory.
                if (width < MIN_SIDE || height < MIN_SIDE) {
                    throw new BadRequestException("La foto es demasiado pequeña.");
                }
                if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
                    throw new BadRequestException("La foto es demasiado grande.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BadRequestException bad) {
                throw bad;
            }
            throw new BadRequestException("No se pudo leer la foto.");
        }
    }

    private static BufferedImage squareCrop(BufferedImage image) {
        int side = Math.min(image.getWidth(), image.getHeight());
        return image.getSubimage((image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side);
    }

    /** Halves the picture step by step while it is much bigger than needed, which keeps it sharp. */
    private static BufferedImage resize(BufferedImage source) {
        BufferedImage current = source;
        while (current.getWidth() > SIZE * 2) {
            current = scale(current, current.getWidth() / 2);
        }
        return scale(current, SIZE);
    }

    private static BufferedImage scale(BufferedImage source, int side) {
        BufferedImage target = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE); // transparent PNG areas become white (JPEG has no alpha)
            g.fillRect(0, 0, side, side);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, side, side, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
            stream.flush();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not encode the profile photo", ex);
        } finally {
            writer.dispose();
        }
    }
}
