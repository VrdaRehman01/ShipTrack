package com.shiptrack.auth.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shiptrack.auth.repository.UserRepository;
import com.shiptrack.auth.service.UserService;
import com.shiptrack.common.security.Role;
import org.junit.jupiter.api.Test;

class AdminBootstrapTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserService userService = mock(UserService.class);

    private void runWith(String email, String password) {
        new AdminBootstrap(new AdminProperties(email, password), users, userService).run(null);
    }

    @Test
    void createsTheAdminOnFirstStart() {
        when(users.existsByEmail("admin@example.com")).thenReturn(false);

        runWith(" Admin@Example.com ", "Admin-pass-123");

        verify(userService).createUser("admin@example.com", "Admin-pass-123", "ShipTrack Admin", Role.ADMIN);
    }

    @Test
    void leavesAnExistingAdminAlone() {
        when(users.existsByEmail("admin@example.com")).thenReturn(true);

        runWith("admin@example.com", "Admin-pass-123");

        verify(userService, never()).createUser(any(), any(), any(), any());
    }

    @Test
    void doesNothingWithoutBothSettings() {
        runWith("admin@example.com", "");
        runWith("", "Admin-pass-123");
        runWith(null, null);

        verify(users, never()).existsByEmail(any());
        verify(userService, never()).createUser(any(), any(), any(), any());
    }
}
