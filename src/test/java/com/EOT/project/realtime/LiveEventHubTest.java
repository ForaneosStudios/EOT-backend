package com.EOT.project.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LiveEventHubTest {

    /** Hub whose emitters are spies, so sends can be verified or made to fail. */
    private static LiveEventHub hub() {
        return new LiveEventHub() {
            @Override
            SseEmitter createEmitter() {
                return spy(new SseEmitter(1000L));
            }
        };
    }

    @Test
    void deliversEventsOnlyToTheirPlayerAndBroadcastsToEveryone() throws IOException {
        LiveEventHub hub = hub();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        SseEmitter aliceStream = hub.subscribe(alice);
        SseEmitter bobStream = hub.subscribe(bob);
        assertEquals(2, hub.openStreams());
        clearInvocations(aliceStream, bobStream);

        hub.onLiveEvent(LiveEvent.toUser(alice, LiveEvent.RECORD, Map.of("bestTimeMs", 9000L)));
        verify(aliceStream).send(any(SseEmitter.SseEventBuilder.class));
        verify(bobStream, never()).send(any(SseEmitter.SseEventBuilder.class));

        hub.onLiveEvent(LiveEvent.broadcast(LiveEvent.LEADERBOARD));
        verify(aliceStream, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(bobStream).send(any(SseEmitter.SseEventBuilder.class));

        hub.heartbeat();
        verify(bobStream, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertDoesNotThrow(() -> hub.onLiveEvent(LiveEvent.toUser(UUID.randomUUID(), LiveEvent.ACHIEVEMENTS, null)));
    }

    @Test
    void dropsStreamsWhoseClientWentAway() throws IOException {
        LiveEventHub hub = hub();
        UUID alice = UUID.randomUUID();
        SseEmitter stream = hub.subscribe(alice);
        doThrow(new IOException("broken pipe")).when(stream).send(any(SseEmitter.SseEventBuilder.class));

        hub.heartbeat();
        assertEquals(0, hub.openStreams());
        assertTrue(hub.streamsOf(alice).isEmpty());
    }

    @Test
    void keepsAtMostFiveStreamsPerPlayer() {
        LiveEventHub hub = hub();
        UUID alice = UUID.randomUUID();
        SseEmitter first = hub.subscribe(alice);
        for (int i = 0; i < LiveEventHub.MAX_STREAMS_PER_USER; i++) {
            hub.subscribe(alice);
        }
        assertEquals(LiveEventHub.MAX_STREAMS_PER_USER, hub.streamsOf(alice).size());
        assertFalse(hub.streamsOf(alice).contains(first));
    }

    @Test
    void realEmittersUseTheConfiguredTimeout() {
        assertEquals(LiveEventHub.STREAM_TIMEOUT.toMillis(), new LiveEventHub().createEmitter().getTimeout());
    }
}
