package com.EOT.project.logging;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One access-log line per API request, so what the PWA sends can be followed in the server logs
 * (Render): method, path, status, duration, who, the request id and the offline-sync headers.
 *
 * <p>Registered before Spring Security so rejected requests (401/403) are logged too. Never logs
 * bodies, tokens or the query string (the password-reset link carries its token there). Header
 * values are cleaned of control characters and truncated, so a client cannot forge log lines.
 * Also runs on the async dispatch of a stream (SSE), which records how a live stream ended.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    /** Set by error handlers so the access line can say which application error was returned. */
    public static final String ERROR_CODE_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".ERROR_CODE";
    static final String USER_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".USER";
    static final String ROLE_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".ROLE";
    private static final String ID_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".ID";
    private static final String START_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".START";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{8,64}");
    private static final int MAX_VALUE = 120;
    private static final Logger log = LoggerFactory.getLogger("eot.http");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        return !path.startsWith(context + "/api/") && !path.startsWith(context + "/actuator/");
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean asyncDispatch = request.getDispatcherType() == DispatcherType.ASYNC;
        String requestId = asyncDispatch && request.getAttribute(ID_ATTRIBUTE) instanceof String known
                ? known : newRequestId(request);
        long start = asyncDispatch && request.getAttribute(START_ATTRIBUTE) instanceof Long first
                ? first : System.nanoTime();
        request.setAttribute(ID_ATTRIBUTE, requestId);
        request.setAttribute(START_ATTRIBUTE, start);
        MDC.put(RequestIds.MDC_KEY, requestId);
        if (!asyncDispatch) {
            response.setHeader(REQUEST_ID_HEADER, requestId);
        }
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error ex) {
            failure = ex;
            throw ex;
        } finally {
            try {
                logRequest(request, response, requestId, start, asyncDispatch, failure);
            } finally {
                MDC.remove(RequestIds.MDC_KEY);
            }
        }
    }

    private static String newRequestId(HttpServletRequest request) {
        String inbound = request.getHeader(REQUEST_ID_HEADER);
        return inbound != null && SAFE_ID.matcher(inbound.trim()).matches() ? inbound.trim() : UUID.randomUUID().toString();
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, String requestId, long start,
                            boolean asyncDispatch, Throwable failure) {
        int status = failure != null ? 500 : response.getStatus();
        boolean health = request.getRequestURI().startsWith(request.getContextPath() + "/actuator/");
        if (health && status < 400) {
            if (!log.isDebugEnabled()) {
                return; // Render probes the health check every few seconds.
            }
        } else if (!log.isInfoEnabled()) {
            return;
        }
        boolean streaming = request.isAsyncStarted() && !asyncDispatch;
        StringBuilder line = new StringBuilder(220).append("http")
                .append(" method=").append(clean(request.getMethod()))
                .append(" path=").append(clean(request.getRequestURI()))
                .append(" status=").append(status)
                .append(" ms=").append((System.nanoTime() - start) / 1_000_000)
                .append(" user=").append(attribute(request, USER_ATTRIBUTE, "anon"))
                .append(" role=").append(attribute(request, ROLE_ATTRIBUTE, "-"))
                .append(" requestId=").append(requestId);
        append(line, "idempotencyKey", request.getHeader("Idempotency-Key"));
        append(line, "replayed", response.getHeader("Idempotency-Replayed"));
        append(line, "intentAt", request.getHeader("X-Client-Intent-At"));
        append(line, "origin", request.getHeader("Origin"));
        append(line, "error", request.getAttribute(ERROR_CODE_ATTRIBUTE) instanceof String code ? code : null);
        if (failure != null) {
            append(line, "exception", failure.getClass().getSimpleName());
        }
        if (streaming) {
            line.append(" stream=open");
        } else if (asyncDispatch) {
            line.append(" stream=closed");
        }
        if (health && status < 400) {
            log.debug("{}", line);
        } else if (status >= 500) {
            log.warn("{}", line);
        } else {
            log.info("{}", line);
        }
    }

    private static void append(StringBuilder line, String name, String value) {
        if (value != null && !value.isBlank()) {
            line.append(' ').append(name).append('=').append(clean(value));
        }
    }

    private static String attribute(HttpServletRequest request, String name, String fallback) {
        return request.getAttribute(name) instanceof String value ? value : fallback;
    }

    /** Removes control characters (no forged lines) and caps the length. */
    static String clean(String value) {
        if (value == null) {
            return "-";
        }
        StringBuilder out = new StringBuilder(Math.min(value.length(), MAX_VALUE));
        for (int i = 0; i < value.length() && out.length() < MAX_VALUE; i++) {
            char c = value.charAt(i);
            out.append(Character.isISOControl(c) || Character.isWhitespace(c) ? '_' : c);
        }
        return out.toString();
    }

    /**
     * Runs inside the security chain, after authorization, and remembers who made the request so the
     * outer logging filter can print it (the security context is already cleared by then). Only the
     * first 8 characters of the account id are kept.
     */
    public static class UserMarker extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
                request.setAttribute(USER_ATTRIBUTE, clean(auth.getName().length() > 8 ? auth.getName().substring(0, 8) : auth.getName()));
                request.setAttribute(ROLE_ATTRIBUTE, auth.getAuthorities().stream()
                        .map(a -> a.getAuthority().replace("ROLE_", "")).findFirst().orElse("-"));
            }
            chain.doFilter(request, response);
        }
    }
}
