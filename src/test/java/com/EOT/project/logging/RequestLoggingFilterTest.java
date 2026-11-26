package com.EOT.project.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestLoggingFilterTest {
    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger("eot.http");
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level previous;

    @BeforeEach
    void attach() {
        previous = logger.getLevel();
        logger.setLevel(Level.INFO);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(events);
        logger.setLevel(previous);
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private List<String> lines() {
        return events.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void logsMethodPathStatusAndOfflineSyncHeaders() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/posts");
        request.addHeader("Idempotency-Key", "7038759e-f2de-44b7-96e5-ef0570c4a1b4");
        request.addHeader("X-Client-Intent-At", "2026-11-26T03:54:41.978Z");
        request.addHeader("Origin", "https://eot.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);
        response.setHeader("Idempotency-Replayed", "true");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(lines()).singleElement().satisfies(line -> assertThat(line)
                .startsWith("http method=POST path=/api/v1/posts status=201 ms=")
                .contains("user=anon", "role=-", "idempotencyKey=7038759e-f2de-44b7-96e5-ef0570c4a1b4",
                        "replayed=true", "intentAt=2026-11-26T03:54:41.978Z", "origin=https://eot.example"));
        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
        assertThat(lines().get(0)).contains("requestId=" + response.getHeader("X-Request-Id"));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void neverLogsTheQueryStringBecauseItCanCarryTheResetToken() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/auth/password-reset/validate");
        request.setQueryString("token=SECRET-TOKEN");
        request.addHeader("Authorization", "Bearer secret.jwt.value");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(lines()).singleElement().satisfies(line -> assertThat(line)
                .doesNotContain("SECRET-TOKEN", "secret.jwt.value", "token="));
    }

    @Test
    void showsWhoMadeTheRequestWithAShortAccountIdAndRole() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/users/me");
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("11111111-1111-4111-8111-111111111111", "n/a", "ROLE_ADMIN"));
        new RequestLoggingFilter.UserMarker().doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(lines()).singleElement().satisfies(line -> assertThat(line)
                .contains("user=11111111", "role=ADMIN").doesNotContain("4111-8111"));
    }

    @Test
    void reportsTheApplicationErrorCodeSetByTheErrorHandlers() throws Exception {
        MockHttpServletRequest request = request("PUT", "/api/v1/posts/abc");
        request.setAttribute(RequestLoggingFilter.ERROR_CODE_ATTRIBUTE, "STALE_WRITE");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(409);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(lines()).singleElement().satisfies(line -> assertThat(line).contains("status=409", "error=STALE_WRITE"));
    }

    @Test
    void keepsAValidInboundRequestIdAndReplacesAnInvalidOne() throws Exception {
        MockHttpServletRequest good = request("GET", "/api/v1/threads");
        good.addHeader("X-Request-Id", "trace-12345678");
        MockHttpServletResponse goodResponse = new MockHttpServletResponse();
        filter.doFilter(good, goodResponse, new MockFilterChain());
        assertThat(goodResponse.getHeader("X-Request-Id")).isEqualTo("trace-12345678");

        MockHttpServletRequest bad = request("GET", "/api/v1/threads");
        bad.addHeader("X-Request-Id", "bad id\nforged line");
        MockHttpServletResponse badResponse = new MockHttpServletResponse();
        filter.doFilter(bad, badResponse, new MockFilterChain());
        assertThat(badResponse.getHeader("X-Request-Id")).matches("[0-9a-f-]{36}");
    }

    @Test
    void controlCharactersInHeadersCannotForgeLogLines() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/threads");
        request.addHeader("Origin", "https://evil.example\nhttp method=DELETE path=/forged status=200");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(lines()).singleElement().satisfies(line -> assertThat(line).doesNotContain("\n", "\r"));
    }

    @Test
    void healthChecksAreNotLoggedAtInfoButFailuresAre() throws Exception {
        filter.doFilter(request("GET", "/actuator/health"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(events.list).isEmpty();

        MockHttpServletResponse down = new MockHttpServletResponse();
        down.setStatus(503);
        filter.doFilter(request("GET", "/actuator/health"), down, new MockFilterChain());
        assertThat(lines()).singleElement().satisfies(line -> assertThat(line).contains("path=/actuator/health", "status=503"));
        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void ignoresRequestsOutsideTheApi() throws Exception {
        filter.doFilter(request("GET", "/index.html"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(events.list).isEmpty();
    }

    @Test
    void logsAnUnhandledFailureAsServerErrorAndRethrowsIt() {
        MockHttpServletRequest request = request("GET", "/api/v1/threads");
        MockFilterChain boom = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) throws ServletException {
                throw new ServletException("boom");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), boom)).isInstanceOf(ServletException.class);

        assertThat(lines()).singleElement().satisfies(line -> assertThat(line).contains("status=500", "exception=ServletException"));
        assertThat(events.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void marksAStreamAsOpenWhenAsyncStartsAndClosedOnItsDispatch() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/live/me");
        request.setAsyncSupported(true);
        MockFilterChain startsStream = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                req.startAsync();
            }
        };
        filter.doFilter(request, new MockHttpServletResponse(), startsStream);
        assertThat(lines().get(0)).contains("path=/api/v1/live/me", "stream=open");

        request.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(lines().get(1)).contains("stream=closed");
        String firstId = lines().get(0).replaceAll(".*requestId=(\\S+).*", "$1");
        assertThat(lines().get(1)).contains("requestId=" + firstId);
    }

    @Test
    void errorBodiesAndLogsShareTheCurrentRequestId() {
        MDC.put("requestId", "req-abc-12345");
        assertThat(RequestIds.current()).isEqualTo("req-abc-12345");
        MDC.remove("requestId");
        assertThat(RequestIds.current()).matches("[0-9a-f-]{36}");
    }
}
