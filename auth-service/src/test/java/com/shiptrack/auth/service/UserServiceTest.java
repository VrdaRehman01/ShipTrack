package com.shiptrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shiptrack.common.security.Role;
import com.shiptrack.auth.entity.User;
import com.shiptrack.auth.exception.EmailAlreadyRegisteredException;
import com.shiptrack.common.error.ApiException;
import com.shiptrack.common.error.CommonErrorCode;
import com.shiptrack.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createUserNormalizesEmailAndHashesPassword() {
        when(userRepository.existsByEmail("ali@example.com")).thenReturn(false);
        when(passwordEncoder.encode("S3cure-pass!")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.createUser("  Ali@Example.COM ", "S3cure-pass!", " Ali Khan ", Role.CUSTOMER);

        assertThat(saved.getEmail()).isEqualTo("ali@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(saved.getFullName()).isEqualTo("Ali Khan");
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void createUserRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("ali@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("ali@example.com", "pw123456", "Ali", Role.CUSTOMER))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createUserTurnsUniqueIndexRaceInto409() {
        when(userRepository.existsByEmail("ali@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> userService.createUser("ali@example.com", "pw123456", "Ali", Role.CUSTOMER))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void createUserRejectsPasswordOver72BytesEvenWhenUnder72Characters() {
        String fortyAccentedLetters = "é".repeat(40); // 40 characters, 80 bytes

        assertThatThrownBy(() -> userService.createUser("ali@example.com", fortyAccentedLetters, "Ali", Role.CUSTOMER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void getByIdThrowsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L)).isInstanceOf(ApiException.class)
                .hasMessage("User not found: 99");
    }
}
