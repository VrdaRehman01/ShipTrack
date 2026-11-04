package com.shiptrack.auth.controller;

import com.shiptrack.auth.dto.CreateUserRequest;
import com.shiptrack.auth.dto.UserResponse;
import com.shiptrack.auth.entity.User;
import com.shiptrack.auth.service.UserService;
import com.shiptrack.common.error.ApiError;
import com.shiptrack.common.security.AuthenticatedUser;
import com.shiptrack.common.security.Role;
import com.shiptrack.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Current user and admin user management")
@ApiResponse(responseCode = "401", description = "Missing or expired JWT",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the logged-in user")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.from(userService.getById(principal.id()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users, optionally by role, newest first (admin)",
            description = "Use role=DRIVER to pick a driver to assign to a shipment.")
    @ApiResponse(responseCode = "403", description = "Not an admin",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<UserResponse> list(
            @Parameter(example = "DRIVER") @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(userService.list(role, page, size), UserResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get any user by id (admin)")
    @ApiResponse(responseCode = "403", description = "Not an admin",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such user",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserResponse getById(@PathVariable Long id) {
        return UserResponse.from(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a user with any role, e.g. a DRIVER (admin)")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "403", description = "Not an admin",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request.email(), request.password(), request.fullName(), request.role());
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.getId())).body(UserResponse.from(user));
    }
}
