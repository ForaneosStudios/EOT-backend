package com.EOT.project.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex,
                                                               HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolvedStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String message = ex.getReason() == null ? "Error en la solicitud" : ex.getReason();
        return response(resolvedStatus, "REQUEST_REJECTED", message, request, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex,
                                                                 HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request, null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex,
                                                           HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex,
                                                         HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud contiene datos invalidos", request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                    HttpServletRequest request) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "La solicitud contiene datos invalidos", request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex,
                                                                  HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_JSON",
                "El cuerpo de la solicitud no contiene un JSON valido", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                             HttpServletRequest request) {
        String message = "El parametro '" + ex.getName() + "' tiene un formato invalido";
        return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                 HttpServletRequest request) {
        String message = "Falta el parametro obligatorio '" + ex.getParameterName() + "'";
        return response(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", message, request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "El metodo HTTP no esta permitido para este recurso", request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                     HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "El tipo de contenido enviado no es compatible", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex,
                                                               HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Debes iniciar sesion para realizar esta operacion", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                             HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "No tienes permiso para realizar esta operacion", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "La operacion entra en conflicto con los datos existentes", request, null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "El recurso fue modificado por otra operacion; vuelve a intentarlo", request, null);
    }

    @ExceptionHandler(TransientDataAccessException.class)
    public ResponseEntity<ErrorResponse> handleTemporaryDatabaseFailure(TransientDataAccessException ex,
                                                                        HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.error("Fallo temporal de base de datos. requestId={}", requestId, ex);
        return response(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "El servicio no esta disponible temporalmente", request, null, requestId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.error("Error inesperado al procesar una solicitud. requestId={}", requestId, ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ha ocurrido un error inesperado en el servidor", request, null, requestId);
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message,
                                                    HttpServletRequest request, List<String> errors) {
        return response(status, code, message, request, errors, UUID.randomUUID().toString());
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message,
                                                    HttpServletRequest request, List<String> errors,
                                                    String requestId) {
        ErrorResponse error = ErrorResponse.builder()
                .status(status.value())
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .requestId(requestId)
                .errors(errors)
                .build();
        return ResponseEntity.status(status).body(error);
    }
}
