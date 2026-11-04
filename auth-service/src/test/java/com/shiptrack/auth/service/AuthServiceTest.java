package com.shiptrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shiptrack.auth.dto.AuthResponse;
import com.shiptrack.auth.dto.LoginRequest;
import com.shiptrack.auth.dto.RegisterRequest;
import com.shiptrack.auth.dto.UserResponse;
import com.shiptrack.auth.entity.Role;
import com.shiptrack.auth.entity.User;
import com.shiptrack.auth.exception.InvalidCredentialsException;
import com.shiptrack.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerAlwaysCreatesCustomer() {
        User created = user();
        when(userService.createUser("ali@example.com", "S3cure-pass!", "Ali Khan", Role.CUSTOMER)).thenReturn(created);

        UserResponse response = authService.register(new RegisterRequest("ali@example.com", "S3cure-pass!", "Ali Khan"));

        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        assertThat(response.id()).isEqualTo(7L);
    }

    @Test
    void loginReturnsBearerTokenForCorrectPassword() {
        User user = user();
        when(userRepository.findByEmail("ali@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("S3cure-pass!", "hash")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.expirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.login(new LoginRequest("Ali@Example.com", "S3cure-pass!"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.user().email()).isEqualTo("ali@example.com");
    }

    @Test
    void loginFailsOnWrongPassword() {
        User user = user();
        when(userRepository.findByEmail("ali@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ali@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwtService, never()).generateToken(user);
    }

    @Test
    void loginFailsOnUnknownEmailWithSameError() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is incorrect");
    }

    private static User user() {
        User user = new User("ali@example.com", "hash", "Ali Khan", Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }
}
