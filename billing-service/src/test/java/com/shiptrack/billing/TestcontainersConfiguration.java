package com.shiptrack.billing;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource") // Spring owns the bean and closes the container on shutdown
    MySQLContainer mysqlContainer() {
        // Timeouts make a stalled readiness probe fail fast and retry, instead of hanging for minutes
        // on a half-open connection through Docker Desktop's port proxy.
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"))
                .withUrlParam("connectTimeout", "5000")
                .withUrlParam("socketTimeout", "30000")
                .withStartupTimeout(Duration.ofMinutes(3));
    }
}
