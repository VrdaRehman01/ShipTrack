package com.shiptrack.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "ali.khan@example.com")
        @NotBlank @Email
        String email,

        @Schema(example = "S3cure-pass!")
        @NotBlank
        String password) {
}
