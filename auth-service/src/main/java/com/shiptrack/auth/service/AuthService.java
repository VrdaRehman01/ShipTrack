package com.shiptrack.auth.service;

import com.shiptrack.auth.dto.AuthResponse;
import com.shiptrack.auth.dto.LoginRequest;
import com.shiptrack.auth.dto.RegisterRequest;
import com.shiptrack.auth.dto.UserResponse;
import com.shiptrack.common.security.Role;
import com.shiptrack.auth.entity.User;
import com.shiptrack.auth.exception.InvalidCredentialsException;
import com.shiptrack.auth.repository.UserRepository;
import com.shiptrack.common.error.ApiException;
import com.shiptrack.common.error.CommonErrorCode;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String unknownUserHash;

    public AuthService(
            UserService userService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Public sign-up always creates a CUSTOMER. Drivers and admins are created by an admin. */
    public UserResponse register(RegisterRequest request) {
        User user = userService.createUser(request.email(), request.password(), request.fullName(), Role.CUSTOMER);
        return UserResponse.from(user);
    }

    /** No transaction: a database connection shouldn't be held for the length of a deliberately slow BCrypt check. */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(UserService.normalizeEmail(request.email())).orElse(null);
        // Hash even for an unknown email, so response time doesn't reveal which emails have accounts.
        boolean passwordMatches = passwordEncoder.matches(
                request.password(), user == null ? unknownUserHash : user.getPasswordHash());
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user);
        return AuthResponse.bearer(token, jwtService.expirationSeconds(), UserResponse.from(user));
    }

    /** Swaps a still-valid token for a fresh one. Reads the user again, so a changed role applies straight away. */
    public AuthResponse refresh(Long userId, String currentToken) {
        User user = userRepository.findById(userId).orElseThrow(AuthService::sessionEnded);
        JwtService.Renewal renewal = jwtService.renew(user, currentToken).orElseThrow(AuthService::sessionEnded);
        return AuthResponse.bearer(renewal.token(), renewal.expiresInSeconds(), UserResponse.from(user));
    }

    private static ApiException sessionEnded() {
        return new ApiException(CommonErrorCode.UNAUTHORIZED, "Your session has ended. Log in again.");
    }
}
