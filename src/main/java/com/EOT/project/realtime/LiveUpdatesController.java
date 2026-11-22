package com.EOT.project.realtime;

import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live stream of the signed-in player's game updates (new record, achievement progress, ranking).
 * It touches no database, so a long-lived stream never holds a connection from the pool.
 */
@RestController
@RequestMapping("/api/v1/live")
public class LiveUpdatesController {
    private final LiveEventHub hub;

    public LiveUpdatesController(LiveEventHub hub) {
        this.hub = hub;
    }

    @GetMapping(value = "/me", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return hub.subscribe(UUID.fromString(authentication.getName()));
    }
}
