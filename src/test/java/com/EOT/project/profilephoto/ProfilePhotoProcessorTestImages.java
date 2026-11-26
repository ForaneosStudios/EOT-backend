package com.EOT.project.profilephoto;

import java.awt.image.BufferedImage;

/** Test helper: a real JPEG as a data URL, for API tests. */
public final class ProfilePhotoProcessorTestImages {
    private ProfilePhotoProcessorTestImages() {
    }

    public static String jpegDataUrl(int width, int height) {
        try {
            return ProfilePhotoProcessorTest.dataUrl("image/jpeg", ProfilePhotoProcessorTest.image("jpg", width, height, BufferedImage.TYPE_INT_RGB));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
