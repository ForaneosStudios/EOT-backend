package com.EOT.project.passwordreset;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints (no session): the user forgot the password, so there is no token to send. */
@RestController
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    public record RequestBodyDto(@NotBlank @Email @Size(max = 150) String email) {}

    public record ConfirmBody(@NotBlank @Size(max = 128) String token, @NotBlank @Size(max = 200) String password) {}

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> request(@Valid @RequestBody RequestBodyDto body, HttpServletRequest http) {
        service.requestReset(body.email(), http.getRemoteAddr());
        return Map.of("sent", true, "expiresInMinutes", PasswordResetService.TTL.toMinutes());
    }

    @GetMapping("/validate")
    public Map<String, Boolean> validate(@RequestParam String token) {
        return Map.of("valid", service.isValid(token));
    }

    @PostMapping("/confirm")
    public Map<String, Boolean> confirm(@Valid @RequestBody ConfirmBody body) {
        service.confirm(body.token(), body.password());
        return Map.of("changed", true);
    }
}
