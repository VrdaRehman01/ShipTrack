package com.shiptrack.shipment.config;

import com.shiptrack.common.web.CorrelationIdInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ShipmentProperties.class)
public class ClientsConfig {

    private static final Logger log = LoggerFactory.getLogger(ClientsConfig.class);

    @Bean
    RestClient billingRestClient(ShipmentProperties props, CorrelationIdInterceptor correlationIdInterceptor) {
        return restClient(props.billing(), correlationIdInterceptor);
    }

    @Bean
    RestClient authRestClient(ShipmentProperties props, CorrelationIdInterceptor correlationIdInterceptor) {
        return restClient(props.auth(), correlationIdInterceptor);
    }

    /**
     * NFR "Reliability": when billing keeps failing, stop calling it for a while (fail fast) instead of
     * slowing down every delivery. Only network errors and 5xx count as failures; a 4xx is our bug, not an outage.
     */
    @Bean
    CircuitBreaker billingCircuitBreaker(
            @Value("${shiptrack.billing.circuit-breaker.wait-in-open-state:30s}") Duration waitInOpenState) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(waitInOpenState)
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .build();
        CircuitBreaker breaker = CircuitBreaker.of("billing", config);
        breaker.getEventPublisher().onStateTransition(e ->
                log.warn("Billing circuit breaker: {}", e.getStateTransition()));
        return breaker;
    }

    private static RestClient restClient(ShipmentProperties.Client client, CorrelationIdInterceptor interceptor) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(client.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(client.readTimeout());
        return RestClient.builder()
                .baseUrl(client.url())
                .requestFactory(requestFactory)
                .requestInterceptor(interceptor)
                .build();
    }
}
