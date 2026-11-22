package com.EOT.project.realtime;

import java.util.Map;
import java.util.UUID;

/**
 * Something the open apps should refresh. Published inside a transaction and delivered only after
 * it commits. {@code authUserId} null means every connected player receives it.
 */
public record LiveEvent(UUID authUserId, String type, Map<String, Object> data) {
    public static final String RECORD = "record";
    public static final String ACHIEVEMENTS = "achievements";
    public static final String LEADERBOARD = "leaderboard";

    public static LiveEvent toUser(UUID authUserId, String type, Map<String, Object> data) {
        return new LiveEvent(authUserId, type, data == null ? Map.of() : data);
    }

    public static LiveEvent broadcast(String type) {
        return new LiveEvent(null, type, Map.of());
    }
}
