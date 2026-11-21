package com.EOT.project.idempotency;

import com.EOT.project.model.IdempotencyKeyModel;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in idempotency for authenticated API mutations.
 *
 * <p>Requests WITHOUT an {@code Idempotency-Key} header pass through untouched, so
 * clients that never send it (mobile app, game) see no change. With the header:
 * <ul>
 *   <li>first request: runs normally and its response is stored;</li>
 *   <li>retry with the same key and body: the stored response is returned without running
 *       the request again, plus {@code Idempotency-Replayed: true};</li>
 *   <li>retry while the first is still running: {@code 409 IDEMPOTENCY_IN_PROGRESS} with
 *       {@code Retry-After};</li>
 *   <li>same key with a different request: {@code 422 IDEMPOTENCY_KEY_REUSED}.</li>
 * </ul>
 *
 * <p>The request runs inside one transaction that the services join, and a successful response
 * is stored in that same transaction. The change and its stored response therefore commit
 * together: a crash before the commit leaves nothing behind (a later retry runs it once), and a
 * crash after it leaves a response to replay. Responses that changed nothing and are deterministic
 * (400, 404, 409, 410, 412, 422) are stored separately; 5xx, 401, 403, 408 and 429 release the key
 * so a later retry really runs again.
 *
 * <p>It is registered inside the security chain after authorization (deliberately not as a
 * servlet filter bean), so keys are scoped per authenticated user and rejected requests never
 * use one.
 */
public class IdempotencyFilter extends OncePerRequestFilter {
    public static final String KEY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotency-Replayed";
    static final int MAX_BODY_BYTES = 1_048_576;
    static final String RETRY_AFTER_SECONDS = "2";
    static final String UNAVAILABLE_RETRY_AFTER_SECONDS = "5";

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<Integer> STORABLE_CLIENT_ERRORS = Set.of(400, 404, 409, 410, 412, 422);

    private final IdempotencyService service;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public IdempotencyFilter(IdempotencyService service, ObjectMapper objectMapper,
                             PlatformTransactionManager transactionManager) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.transactionManager = transactionManager;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(KEY_HEADER) == null
                || !MUTATING_METHODS.contains(request.getMethod())
                || !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getHeader(KEY_HEADER).trim();
        if (!KEY_PATTERN.matcher(key).matches()) {
            writeError(request, response, 400, "IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key debe tener entre 8 y 128 caracteres [A-Za-z0-9._:-]");
            return;
        }

        byte[] body;
        try {
            body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        } catch (IOException ex) {
            log.warn("Could not read request body for idempotent request: {}", ex.getMessage());
            writeError(request, response, 400, "BAD_REQUEST", "No se pudo leer el cuerpo de la solicitud");
            return;
        }
        if (body.length > MAX_BODY_BYTES) {
            writeError(request, response, 413, "PAYLOAD_TOO_LARGE", "El cuerpo de la solicitud es demasiado grande");
            return;
        }

        String path = request.getRequestURI();
        String hash = hash(request.getMethod(), path, request.getQueryString(), body);
        IdempotencyService.Decision decision;
        try {
            decision = service.begin(auth.getName(), key, request.getMethod(), path, hash);
        } catch (RuntimeException ex) {
            // Without bookkeeping the request could run twice; ask the client to retry instead.
            log.warn("Idempotency bookkeeping unavailable: {}", ex.getMessage());
            writeUnavailable(request, response);
            return;
        }

        switch (decision.outcome()) {
            case REPLAY -> replay(decision.entry(), response);
            case MISMATCH -> writeError(request, response, 422, "IDEMPOTENCY_KEY_REUSED",
                    "Esta Idempotency-Key ya se uso con una solicitud diferente");
            case IN_PROGRESS -> writeInProgress(request, response);
            case PROCEED -> execute(new CachedBodyHttpServletRequest(request, body), response, chain, decision);
        }
    }

    private void execute(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                         IdempotencyService.Decision decision) throws ServletException, IOException {
        UUID recordId = decision.entry().getId();
        Instant lease = decision.lease();

        TransactionStatus transaction;
        try {
            DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
            definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            definition.setName("idempotent " + request.getMethod() + " " + request.getRequestURI());
            transaction = transactionManager.getTransaction(definition);
        } catch (RuntimeException ex) {
            log.warn("Could not open transaction for idempotent request: {}", ex.getMessage());
            safely(() -> service.release(recordId, lease));
            writeUnavailable(request, response);
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        boolean finished = false;
        try {
            chain.doFilter(request, wrapper);
            finished = true;
        } finally {
            if (!finished) {
                rollbackQuietly(transaction);
                safely(() -> service.release(recordId, lease));
            }
        }

        int status = wrapper.getStatus();
        byte[] content = wrapper.getContentAsByteArray();
        IdempotencyService.StoredResponse stored = new IdempotencyService.StoredResponse(status,
                content.length == 0 ? null : new String(content, StandardCharsets.UTF_8),
                wrapper.getContentType(), wrapper.getHeader(HttpHeaders.LOCATION));
        boolean success = status >= 200 && status < 300;

        if (success && !transaction.isRollbackOnly() && content.length <= MAX_BODY_BYTES) {
            commitWithResponse(request, wrapper, transaction, new Ownership(recordId, lease), stored);
        } else {
            rollbackQuietly(transaction);
            if (success) {
                // The changes were not saved, so never report success to the client.
                log.warn("Successful response with a rolled-back transaction on {}", request.getRequestURI());
                safely(() -> service.release(recordId, lease));
                replaceWithUnavailable(request, wrapper);
            } else if (isStorable(status) && content.length <= MAX_BODY_BYTES) {
                safely(() -> service.complete(recordId, lease, stored));
            } else {
                safely(() -> service.release(recordId, lease));
            }
        }
        wrapper.copyBodyToResponse();
    }

    /** The key record a request claimed and the lease that proves it still owns it. */
    private record Ownership(UUID recordId, Instant lease) { }

    private void commitWithResponse(HttpServletRequest request, ContentCachingResponseWrapper wrapper,
                                    TransactionStatus transaction, Ownership ownership,
                                    IdempotencyService.StoredResponse stored) throws IOException {
        UUID recordId = ownership.recordId();
        Instant lease = ownership.lease();
        try {
            transaction.flush(); // Surface constraint violations before claiming the response.
            if (!service.completeInCurrentTransaction(recordId, lease, stored)) {
                // A retry took over after our lease expired; it will apply the action instead.
                rollbackQuietly(transaction);
                replaceWithInProgress(request, wrapper);
                return;
            }
            transactionManager.commit(transaction);
        } catch (DataIntegrityViolationException ex) {
            rollbackQuietly(transaction);
            safely(() -> service.release(recordId, lease));
            replaceWithError(request, wrapper, 409, "DATA_CONFLICT",
                    "La operacion entra en conflicto con los datos existentes");
        } catch (RuntimeException ex) {
            log.error("Could not commit idempotent request on {}", request.getRequestURI(), ex);
            rollbackQuietly(transaction);
            safely(() -> service.release(recordId, lease));
            replaceWithUnavailable(request, wrapper);
        }
    }

    private void rollbackQuietly(TransactionStatus transaction) {
        if (transaction.isCompleted()) {
            return;
        }
        try {
            transactionManager.rollback(transaction);
        } catch (RuntimeException ex) {
            log.warn("Rollback of idempotent request failed: {}", ex.getMessage());
        }
    }

    private void replay(IdempotencyKeyModel saved, HttpServletResponse response) throws IOException {
        response.setStatus(saved.getResponseStatus());
        response.setHeader(REPLAYED_HEADER, "true");
        if (saved.getResponseContentType() != null) {
            response.setContentType(saved.getResponseContentType());
        }
        if (saved.getLocationHeader() != null) {
            response.setHeader(HttpHeaders.LOCATION, saved.getLocationHeader());
        }
        if (saved.getResponseBody() != null) {
            byte[] bytes = saved.getResponseBody().getBytes(StandardCharsets.UTF_8);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
        }
    }

    static boolean isStorable(int status) {
        return (status >= 200 && status < 300) || STORABLE_CLIENT_ERRORS.contains(status);
    }

    static String hash(String method, String path, String query, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((method + "\n" + path + "\n" + (query == null ? "" : query) + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private void writeInProgress(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        writeError(request, response, 409, "IDEMPOTENCY_IN_PROGRESS",
                "Esta solicitud ya se esta procesando; reintenta en unos segundos");
    }

    private void writeUnavailable(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, UNAVAILABLE_RETRY_AFTER_SECONDS);
        writeError(request, response, 503, "SERVICE_UNAVAILABLE", "El servicio no esta disponible temporalmente");
    }

    // The cached body has not reached the client yet, so it can still be swapped for an error.
    private void replaceWithInProgress(HttpServletRequest request, ContentCachingResponseWrapper wrapper)
            throws IOException {
        wrapper.resetBuffer();
        writeInProgress(request, wrapper);
    }

    private void replaceWithUnavailable(HttpServletRequest request, ContentCachingResponseWrapper wrapper)
            throws IOException {
        wrapper.resetBuffer();
        writeUnavailable(request, wrapper);
    }

    private void replaceWithError(HttpServletRequest request, ContentCachingResponseWrapper wrapper, int status,
                                  String code, String message) throws IOException {
        wrapper.resetBuffer();
        writeError(request, wrapper, status, code, message);
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, int status, String code,
                            String message) throws IOException {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("status", status);
        error.put("code", code);
        error.put("message", message);
        error.put("timestamp", Instant.now().toString());
        error.put("path", request.getRequestURI());
        error.put("requestId", UUID.randomUUID().toString());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error);
    }

    private static void safely(Runnable bookkeeping) {
        try {
            bookkeeping.run();
        } catch (RuntimeException ex) {
            // Bookkeeping must never turn a business response into an error.
            log.warn("Idempotency bookkeeping failed: {}", ex.getMessage());
        }
    }
}
