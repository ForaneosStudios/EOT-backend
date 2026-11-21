package com.EOT.project.idempotency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencySupportTest {

    @Test
    void cleanupJobPurgesExpiredKeysAndSwallowsFailures() {
        IdempotencyService service = mock(IdempotencyService.class);
        IdempotencyCleanupJob job = new IdempotencyCleanupJob(service);

        when(service.deleteExpired()).thenReturn(3);
        job.purgeExpired();
        verify(service).deleteExpired();

        when(service.deleteExpired()).thenThrow(new IllegalStateException("database down"));
        assertDoesNotThrow(job::purgeExpired);

        doReturn(0).when(service).deleteExpired();
        assertDoesNotThrow(job::purgeExpired);
    }

    @Test
    void cachedRequestReplaysTheBodyAsStreamAndReader() throws IOException {
        byte[] body = "{\"nombre\":\"Ñandú\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest original = new MockHttpServletRequest("POST", "/api/v1/posts");
        original.setCharacterEncoding("UTF-8");
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(original, body);

        assertEquals(body.length, cached.getContentLength());
        assertEquals(body.length, cached.getContentLengthLong());

        var stream = cached.getInputStream();
        assertTrue(stream.isReady());
        assertFalse(stream.isFinished());
        assertArrayEquals(body, stream.readAllBytes());
        assertTrue(stream.isFinished());
        assertEquals(-1, stream.read());
        assertThrows(UnsupportedOperationException.class, () -> stream.setReadListener(null));

        // A second read gets the full body again.
        assertEquals("{\"nombre\":\"Ñandú\"}", new String(cached.getInputStream().readNBytes(body.length), StandardCharsets.UTF_8));
        assertEquals("{\"nombre\":\"Ñandú\"}", cached.getReader().readLine());
    }

    @Test
    void cachedRequestDefaultsToUtf8WhenNoEncodingIsDeclared() throws IOException {
        MockHttpServletRequest original = new MockHttpServletRequest();
        original.setCharacterEncoding((String) null);
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(original, "año".getBytes(StandardCharsets.UTF_8));
        assertEquals("año", cached.getReader().readLine());
    }
}
