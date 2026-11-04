package com.shiptrack.auth.config;

import com.shiptrack.common.security.Role;
import com.shiptrack.auth.repository.UserRepository;
import com.shiptrack.auth.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN on startup when ADMIN_EMAIL and ADMIN_PASSWORD are set.
 * Public sign-up can't create admins, so this is how you get the first one without
 * hard-coding a password in a migration.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final UserService userService;

    public AdminBootstrap(AdminProperties properties, UserRepository userRepository, UserService userService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            return;
        }
        String email = UserService.normalizeEmail(properties.email());
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userService.createUser(email, properties.password(), "ShipTrack Admin", Role.ADMIN);
        log.info("Created bootstrap admin account {}", email);
    }
}
