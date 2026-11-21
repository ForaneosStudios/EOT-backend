package com.EOT.project.idempotency;

import com.EOT.project.model.IdempotencyKeyModel;
import com.EOT.project.model.IdempotencyStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Error paths of the filter that are hard to provoke against a real database. */
class IdempotencyFilterTest {
    private final IdempotencyService service = mock(IdempotencyService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final TransactionStatus transaction = mock(TransactionStatus.class);
    private final IdempotencyFilter filter = new IdempotencyFilter(service, new ObjectMapper(), transactions);
    private final UUID recordId = UUID.randomUUID();
    private final Instant lease = Instant.parse("2026-11-21T12:01:00Z");

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("sub-1", null, "ROLE_USER"));
        IdempotencyKeyModel claimed = IdempotencyKeyModel.builder().id(recordId).status(IdempotencyStatus.IN_PROGRESS)
                .lockedUntil(lease).build();
        when(service.begin(any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyService.Decision(IdempotencyService.Outcome.PROCEED, claimed, lease));
        when(transactions.getTransaction(any())).thenReturn(transaction);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.addHeader(IdempotencyFilter.KEY_HEADER, "key-12345678");
        request.setContent("{\"content\":\"hola\"}".getBytes());
        return request;
    }

    private FilterChain respondingWith(int status) {
        return (req, res) -> {
            ((HttpServletResponse) res).setStatus(status);
            res.setContentType("application/json");
            res.getWriter().write("{\"ok\":true}");
        };
    }

    @Test
    void storesResponseInsideTheBusinessTransactionAndCommits() throws Exception {
        when(service.completeInCurrentTransaction(eq(recordId), eq(lease), any())).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, respondingWith(201));
        assertEquals(201, response.getStatus());
        verify(transaction).flush();
        verify(transactions).commit(transaction);
        verify(service, never()).release(any(), any());
    }

    @Test
    void commitFailureBecomesRetryable503AndReleasesKey() throws Exception {
        when(service.completeInCurrentTransaction(any(), any(), any())).thenReturn(true);
        doThrow(new TransactionSystemException("connection lost")).when(transactions).commit(transaction);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, respondingWith(201));
        assertEquals(503, response.getStatus());
        assertEquals(IdempotencyFilter.UNAVAILABLE_RETRY_AFTER_SECONDS, response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("SERVICE_UNAVAILABLE"));
        verify(service).release(recordId, lease);
    }

    @Test
    void constraintViolationOnFlushBecomesDataConflict() throws Exception {
        doThrow(new DataIntegrityViolationException("duplicate")).when(transaction).flush();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, respondingWith(201));
        assertEquals(409, response.getStatus());
        assertTrue(response.getContentAsString().contains("DATA_CONFLICT"));
        verify(transactions).rollback(transaction);
        verify(service).release(recordId, lease);
    }

    @Test
    void requestThatLostItsLeaseRollsBackAndReportsInProgress() throws Exception {
        when(service.completeInCurrentTransaction(any(), any(), any())).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, respondingWith(201));
        assertEquals(409, response.getStatus());
        assertTrue(response.getContentAsString().contains("IDEMPOTENCY_IN_PROGRESS"));
        verify(transactions).rollback(transaction);
        verify(transactions, never()).commit(any());
        verify(service, never()).release(any(), any()); // The retry owns the key now.
    }

    @Test
    void successWithRollbackOnlyTransactionIsNeverReportedAsSuccess() throws Exception {
        when(transaction.isRollbackOnly()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, respondingWith(200));
        assertEquals(503, response.getStatus());
        verify(service).release(recordId, lease);
    }

    @Test
    void businessErrorRollsBackAndStoresTheErrorSeparately() throws Exception {
        when(transaction.isRollbackOnly()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, respondingWith(409));
        assertEquals(409, response.getStatus());
        verify(transactions).rollback(transaction);
        verify(service).complete(eq(recordId), eq(lease), argThat(stored -> stored.status() == 409));
    }

    @Test
    void serverErrorReleasesKeySoTheRetryRunsAgain() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, respondingWith(500));
        assertEquals(500, response.getStatus());
        verify(service).release(recordId, lease);
    }

    @Test
    void unexpectedExceptionRollsBackReleasesAndPropagates() {
        FilterChain failing = (req, res) -> { throw new IllegalStateException("boom"); };
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, failing));
        verify(transactions).rollback(transaction);
        verify(service).release(recordId, lease);
    }

    @Test
    void bookkeepingOutageAnswers503WithoutRunningTheRequest() throws Exception {
        when(service.begin(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("db down"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, chain);
        assertEquals(503, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void transactionThatCannotOpenAnswers503AndReleasesKey() throws Exception {
        when(transactions.getTransaction(any())).thenThrow(new IllegalStateException("pool exhausted"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, chain);
        assertEquals(503, response.getStatus());
        verifyNoInteractions(chain);
        verify(service).release(recordId, lease);
    }

    @Test
    void requestsWithoutHeaderAreNotTouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
        verifyNoInteractions(service, transactions);
    }
}
