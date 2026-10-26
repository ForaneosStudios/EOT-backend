package com.EOT.project.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(response, request, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                "Debes iniciar sesion con un token valido");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, request, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                "No tienes permiso para realizar esta operacion");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, int status,
                       String code, String message) throws IOException {
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
}
