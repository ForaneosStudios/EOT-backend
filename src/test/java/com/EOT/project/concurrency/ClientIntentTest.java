package com.EOT.project.concurrency;

import com.EOT.project.exception.BadRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class ClientIntentTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC);

    private Instant parse(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) request.addHeader(ClientIntent.HEADER, header);
        return ClientIntent.parse(request, clock);
    }

    @Test
    void absentHeaderMeansLegacyBehaviour() {
        assertNull(parse(null));
        assertNull(parse("  "));
        assertNull(ClientIntent.current()); // No request bound (unit tests, background jobs).
    }

    @Test
    void parsesAndCapsFutureTimes() {
        assertEquals(Instant.parse("2026-11-21T11:00:00Z"), parse("2026-11-21T11:00:00Z"));
        assertEquals(clock.instant(), parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void rejectsMalformedValues() {
        assertThrows(BadRequestException.class, () -> parse("21/11/2026"));
    }

    @Test
    void staleOnlyWhenStrictlyOlderThanLastChange() {
        Instant updated = Instant.parse("2026-11-21T11:00:00Z");
        assertTrue(ClientIntent.isStale(updated.minusMillis(1), updated, null));
        assertFalse(ClientIntent.isStale(updated, updated, null));
        assertFalse(ClientIntent.isStale(updated.plusSeconds(1), updated, null));
        assertTrue(ClientIntent.isStale(updated.minusSeconds(1), null, updated)); // Falls back to createdAt.
        assertFalse(ClientIntent.isStale(null, updated, null));
    }
}
