package com.shiptrack.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Public sign-up. New accounts always get the CUSTOMER role.")
public record RegisterRequest(
        @Schema(example = "ali.khan@example.com")
        @NotBlank @Email @Size(max = 255)
        String email,

        // BCrypt only uses the first 72 bytes, so cap it there.
        @Schema(example = "S3cure-pass!")
        @NotBlank @Size(min = 8, max = 72)
        String password,

        @Schema(example = "Ali Khan")
        @NotBlank @Size(max = 100)
        String fullName) {
}
