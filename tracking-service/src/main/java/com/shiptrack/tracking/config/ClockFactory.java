package com.shiptrack.tracking.config;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import java.time.Clock;

@Factory
public class ClockFactory {

    /** Injected instead of calling Instant.now() so tests can control time. */
    @Singleton
    Clock clock() {
        return Clock.systemUTC();
    }
}
