package com.EOT.project.profilephoto;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Keeps the photos in a public Supabase Storage bucket through its REST API, using the service role
 * key (server side only). There is one object per player, so a new photo replaces the old one; the
 * URL carries a version so browsers and the CDN do not keep showing the previous picture.
 */
@Component
public class SupabaseProfilePhotoStorage implements ProfilePhotoStorage {
    private static final Logger log = LoggerFactory.getLogger(SupabaseProfilePhotoStorage.class);
    private final String baseUrl;
    private final String serviceKey;
    private final String bucket;
    private final RestClient http;
    private final Clock clock;

    @Autowired
    public SupabaseProfilePhotoStorage(@Value("${SUPABASE_AUTH_ISSUER:}") String issuer,
                                       @Value("${SUPABASE_SERVICE_ROLE_KEY:}") String serviceKey,
                                       @Value("${app.profile-photos.bucket:profile-photos}") String bucket) {
        this(projectUrl(issuer), serviceKey, bucket, RestClient.create(), Clock.systemUTC());
    }

    SupabaseProfilePhotoStorage(String baseUrl, String serviceKey, String bucket, RestClient http, Clock clock) {
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
        this.bucket = bucket;
        this.http = http;
        this.clock = clock;
    }

    /** "https://ref.supabase.co/auth/v1/" -> "https://ref.supabase.co". */
    static String projectUrl(String issuer) {
        String url = issuer == null ? "" : issuer.trim().replaceAll("/+$", "");
        return url.endsWith("/auth/v1") ? url.substring(0, url.length() - "/auth/v1".length()) : url;
    }

    boolean isConfigured() {
        return !baseUrl.isBlank() && !serviceKey.isBlank();
    }

    private static String objectName(UUID userId) {
        return userId + ".jpg";
    }

    @Override
    public String upload(UUID userId, byte[] jpeg) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El almacenamiento de fotos no está disponible");
        }
        try {
            http.post().uri(baseUrl + "/storage/v1/object/{bucket}/{name}", bucket, objectName(userId))
                    .header("apikey", serviceKey).header("Authorization", "Bearer " + serviceKey)
                    .header("x-upsert", "true").header("Cache-Control", "max-age=3600")
                    .contentType(MediaType.IMAGE_JPEG).body(jpeg)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("Profile photo upload failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo guardar la foto. Inténtalo de nuevo.", ex);
        }
        return baseUrl + "/storage/v1/object/public/" + bucket + "/" + objectName(userId) + "?v=" + clock.millis();
    }

    @Override
    public void delete(UUID userId) {
        if (!isConfigured()) {
            log.warn("Profile photo not removed from storage: Supabase service role is not configured");
            return;
        }
        try {
            http.delete().uri(baseUrl + "/storage/v1/object/{bucket}/{name}", bucket, objectName(userId))
                    .header("apikey", serviceKey).header("Authorization", "Bearer " + serviceKey)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            // A photo that is already gone, or a storage hiccup, must not block the account change.
            log.warn("Profile photo removal failed: {}", ex.getMessage());
        }
    }
}
