package com.EOT.project.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsApplicationAndInfrastructureExceptions() throws Exception {
        assertError(get("/test/not-found"), 404, "RESOURCE_NOT_FOUND");
        assertError(get("/test/bad-request"), 400, "BAD_REQUEST");
        assertError(get("/test/forbidden-status"), 403, "REQUEST_REJECTED");
        assertError(get("/test/unauthorized"), 401, "UNAUTHORIZED");
        assertError(get("/test/forbidden"), 403, "FORBIDDEN");
        assertError(get("/test/data-conflict"), 409, "DATA_CONFLICT");
        assertError(get("/test/concurrent-update"), 409, "CONCURRENT_UPDATE");
        assertError(get("/test/database-unavailable"), 503, "SERVICE_UNAVAILABLE");
    }

    @Test
    void mapsInvalidRequestsWithoutReturningInternalErrors() throws Exception {
        assertError(get("/test/uuid/not-a-uuid"), 400, "INVALID_PARAMETER");
        assertError(get("/test/required"), 400, "MISSING_PARAMETER");
        assertError(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{"),
                400, "MALFORMED_JSON");
        assertError(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"),
                400, "VALIDATION_ERROR");
        assertError(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("name"),
                415, "UNSUPPORTED_MEDIA_TYPE");
        assertError(put("/test/not-found"), 405, "METHOD_NOT_ALLOWED");
        assertError(get("/test/constraint"), 400, "VALIDATION_ERROR");
    }

    @Test
    void hidesUnexpectedExceptionDetailsAndReturnsRequestId() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Ha ocurrido un error inesperado en el servidor"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/test/unexpected"));
    }

    private void assertError(MockHttpServletRequestBuilder request, int expectedStatus, String expectedCode)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.path").isNotEmpty());
    }

    private record TestBody(@NotBlank String name) {
    }

    @RestController
    @RequestMapping("/test")
    private static class FailingController {

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("No existe");
        }

        @GetMapping("/bad-request")
        void badRequest() {
            throw new BadRequestException("Solicitud invalida");
        }

        @GetMapping("/forbidden-status")
        void forbiddenStatus() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new BadCredentialsException("Token invalido");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("Sin permisos");
        }

        @GetMapping("/data-conflict")
        void dataConflict() {
            throw new DataIntegrityViolationException("duplicate key");
        }

        @GetMapping("/concurrent-update")
        void concurrentUpdate() {
            throw new ObjectOptimisticLockingFailureException("User", 1);
        }

        @GetMapping("/database-unavailable")
        void databaseUnavailable() {
            throw new TransientDataAccessResourceException("database offline");
        }

        @GetMapping("/uuid/{id}")
        void uuid(@PathVariable java.util.UUID id) {
            // The argument conversion is the behavior under test.
        }

        @GetMapping("/required")
        void required(@RequestParam String value) {
            // The required query parameter is the behavior under test.
        }

        @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        void body(@Valid @RequestBody TestBody body) {
            // JSON parsing and validation are the behaviors under test.
        }

        @GetMapping("/constraint")
        void constraint() {
            throw new ConstraintViolationException(Set.of());
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret database detail");
        }
    }
}
