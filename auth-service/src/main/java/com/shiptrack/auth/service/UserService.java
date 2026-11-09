package com.shiptrack.auth.service;

import com.shiptrack.common.security.Role;
import com.shiptrack.auth.entity.User;
import com.shiptrack.auth.exception.EmailAlreadyRegisteredException;
import com.shiptrack.common.error.ApiException;
import com.shiptrack.common.error.CommonErrorCode;
import com.shiptrack.auth.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Emails are case-insensitive: store and look them up in one canonical form. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public User createUser(String email, String rawPassword, String fullName, Role role) {
        return createUser(email, rawPassword, fullName, role, null);
    }

    /** No transaction around this: a database connection shouldn't be held for the length of a BCrypt hash. */
    public User createUser(String email, String rawPassword, String fullName, Role role, String phone) {
        // @Size on the DTOs counts characters, but BCrypt's limit is bytes and it refuses longer input.
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new ApiException(CommonErrorCode.VALIDATION_FAILED,
                    "password: is too long (accents and symbols count as more than one character)");
        }
        String normalized = normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new EmailAlreadyRegisteredException(normalized);
        }
        User user = new User(normalized, passwordEncoder.encode(rawPassword), fullName.trim(), role, phone == null ? null : phone.trim());
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the unique index wins.
            throw new EmailAlreadyRegisteredException(normalized);
        }
    }

    @Transactional
    public User updateProfile(Long id, String fullName, String phone) {
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
        user.updateProfile(fullName.trim(), phone == null ? null : phone.trim());
        return user;
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
    }

    @Transactional(readOnly = true)
    public Page<User> list(Role role, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return role == null ? userRepository.findAll(pageable) : userRepository.findByRole(role, pageable);
    }
}
