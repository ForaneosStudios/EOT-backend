package com.EOT.project.profilephoto;

import java.util.UUID;

/** Where the processed profile photos are kept. */
public interface ProfilePhotoStorage {
    /** Stores (replacing any previous one) the player's photo and returns its public URL. */
    String upload(UUID userId, byte[] jpeg);

    /** Removes the player's photo; a photo that is already gone is not an error. */
    void delete(UUID userId);
}
