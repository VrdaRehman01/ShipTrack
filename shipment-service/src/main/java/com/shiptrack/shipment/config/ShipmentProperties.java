package com.shiptrack.shipment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * shiptrack.clients.* — where the services this one calls live, and how patient to be with them.
 */
@ConfigurationProperties(prefix = "shiptrack.clients")
public record ShipmentProperties(Client billing, Client auth) {

    /**
     * @param url            base URL, e.g. http://billing-service:8083
     * @param connectTimeout give up connecting after this long
     * @param readTimeout    give up waiting for a response after this long
     */
    public record Client(
            String url,
            @DefaultValue("2s") Duration connectTimeout,
            @DefaultValue("3s") Duration readTimeout) {
    }
}
