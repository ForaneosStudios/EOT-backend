package com.EOT.project.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserLoginRequestDTO(
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El formato del correo electrónico no es válido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password
) {
    @Override
    public String toString() {
        return "UserLoginRequestDTO[email=" + email + ", password=[PROTECTED]]";
    }
}
