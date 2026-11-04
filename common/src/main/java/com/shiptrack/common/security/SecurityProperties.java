package com.shiptrack.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * shiptrack.security.* — which paths a service leaves open, and the key for /internal/** calls.
 *
 * @param publicPaths    extra Ant-style paths anyone may call (health and Swagger are always public)
 * @param internalApiKey shared key for service-to-service calls; blank disables /internal/** entirely
 */
@ConfigurationProperties(prefix = "shiptrack.security")
public record SecurityProperties(@DefaultValue List<String> publicPaths, String internalApiKey) {
}
