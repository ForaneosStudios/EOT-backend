package com.EOT.project.logging;

import java.util.UUID;
import org.slf4j.MDC;

/** Identifier shared by the access log line, the error log and the {@code requestId} of error bodies. */
public final class RequestIds {
    public static final String MDC_KEY = "requestId";

    private RequestIds() {
    }

    /** The id of the request being served, or a fresh one when called outside a logged request. */
    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }
}
