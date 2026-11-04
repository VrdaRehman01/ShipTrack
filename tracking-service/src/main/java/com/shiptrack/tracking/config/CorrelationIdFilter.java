package com.shiptrack.tracking.config;

import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Same behaviour as the Spring services: reuse a safe incoming X-Correlation-Id or create one,
 * expose it to logs (MDC) and the request, and echo it in the response.
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public class CorrelationIdFilter implements Ordered {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = "correlationId";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    /** The id for this request (always set by the filter). */
    public static String of(HttpRequest<?> request) {
        return request.getAttribute(ATTRIBUTE, String.class).orElse(null);
    }

    @RequestFilter
    public void assign(HttpRequest<?> request, MutablePropagatedContext context) {
        String incoming = request.getHeaders().get(HEADER);
        String id = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString().substring(0, 8);
        request.setAttribute(ATTRIBUTE, id);
        context.add(new MdcPropagationContext(Map.of(ATTRIBUTE, id)));
    }

    @ResponseFilter
    public void echo(HttpRequest<?> request, MutableHttpResponse<?> response) {
        String id = of(request);
        if (id != null) {
            response.header(HEADER, id);
        }
    }

    @Override
    public int getOrder() {
        // Before security, so even 401s carry the id.
        return ServerFilterPhase.FIRST.order();
    }
}
