package com.shiptrack.auth.dto;

import com.shiptrack.auth.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Admin-only: create a user with any role (e.g. a DRIVER).")
public record CreateUserRequest(
        @Schema(example = "driver.one@example.com")
        @NotBlank @Email @Size(max = 255)
        String email,

        @Schema(example = "Dr1ver-pass!")
        @NotBlank @Size(min = 8, max = 72)
        String password,

        @Schema(example = "Bilal Ahmed")
        @NotBlank @Size(max = 100)
        String fullName,

        @Schema(example = "DRIVER")
        @NotNull
        Role role) {
}
