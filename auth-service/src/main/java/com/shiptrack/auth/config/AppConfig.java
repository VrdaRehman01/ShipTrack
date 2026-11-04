package com.shiptrack.auth.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, AdminProperties.class})
public class AppConfig {

    /** Injected instead of calling Instant.now() so tests can control time (e.g. token expiry). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
