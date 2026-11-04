package com.shiptrack.auth.dto;

import com.shiptrack.auth.entity.Role;
import com.shiptrack.auth.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record UserResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "ali.khan@example.com") String email,
        @Schema(example = "Ali Khan") String fullName,
        @Schema(example = "CUSTOMER") Role role,
        @Schema(example = "2026-11-04T09:30:00Z") Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCreatedAt());
    }
}
