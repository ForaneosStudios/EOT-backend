package com.EOT.project.profilephoto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class SupabaseProfilePhotoStorageTest {
    record Seen(String method, String path, String authorization, String apikey, String upsert, String contentType, byte[] body) { }

    private HttpServer server;
    private final List<Seen> seen = new ArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final UUID user = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private SupabaseProfilePhotoStorage storage;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestHeaders().getFirst("apikey"),
                    exchange.getRequestHeaders().getFirst("x-upsert"), exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_764_000_000_000L), ZoneOffset.UTC);
        storage = new SupabaseProfilePhotoStorage(base, "service-key", "profile-photos", RestClient.create(), clock);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void uploadsTheJpegWithTheServiceKeyAndReturnsAVersionedPublicUrl() {
        byte[] jpeg = {1, 2, 3, 4};

        String url = storage.upload(user, jpeg);

        assertThat(seen).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/storage/v1/object/profile-photos/" + user + ".jpg");
            assertThat(request.authorization()).isEqualTo("Bearer service-key");
            assertThat(request.apikey()).isEqualTo("service-key");
            assertThat(request.upsert()).isEqualTo("true");
            assertThat(request.contentType()).isEqualTo("image/jpeg");
            assertThat(request.body()).isEqualTo(jpeg);
        });
        assertThat(url).endsWith("/storage/v1/object/public/profile-photos/" + user + ".jpg?v=1764000000000");
        assertThat(url.length()).isLessThan(255);
    }

    @Test
    void reportsABadGatewayWhenTheStorageRefusesTheUpload() {
        status.set(500);
        assertThatThrownBy(() -> storage.upload(user, new byte[] {1}))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void deletesThePlayersObject() {
        storage.delete(user);
        assertThat(seen).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("DELETE");
            assertThat(request.path()).isEqualTo("/storage/v1/object/profile-photos/" + user + ".jpg");
            assertThat(request.authorization()).isEqualTo("Bearer service-key");
        });
    }

    @Test
    void aFailedRemovalNeverBlocksTheAccountChange() {
        status.set(404);
        storage.delete(user); // does not throw
        assertThat(seen).hasSize(1);
    }

    @Test
    void withoutStorageConfigurationUploadsAreUnavailableAndRemovalsAreSkipped() {
        SupabaseProfilePhotoStorage unconfigured = new SupabaseProfilePhotoStorage("", "", "profile-photos", RestClient.create(), Clock.systemUTC());
        assertThatThrownBy(() -> unconfigured.upload(user, new byte[] {1}))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        unconfigured.delete(user);
        assertThat(seen).isEmpty();
    }

    @Test
    void derivesTheProjectUrlFromTheAuthIssuer() {
        assertThat(SupabaseProfilePhotoStorage.projectUrl("https://abc.supabase.co/auth/v1/")).isEqualTo("https://abc.supabase.co");
        assertThat(SupabaseProfilePhotoStorage.projectUrl("https://abc.supabase.co")).isEqualTo("https://abc.supabase.co");
        assertThat(SupabaseProfilePhotoStorage.projectUrl(null)).isEmpty();
    }
}
