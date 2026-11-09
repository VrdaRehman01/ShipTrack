package com.shiptrack.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Admin-only: correct someone's name or phone number.")
public record UpdateUserRequest(
        @Schema(example = "Bilal Ahmed")
        @NotBlank @Size(max = 100)
        String fullName,

        @Schema(example = "+92 300 1234567", description = "Leave out to remove the number.", nullable = true)
        @Pattern(regexp = "^\\+?[0-9][0-9 \\-]{6,18}$", message = "must be a phone number like +92 300 1234567")
        String phone) {
}
