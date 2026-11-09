package com.shiptrack.shipment.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.shiptrack.common.security.InternalApiKeyFilter;
import com.shiptrack.common.security.SecurityProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class BillingClientTest {

    private static final String INVOICES_URL = "http://billing/internal/invoices";
    private static final BillingClient.CreateInvoiceRequest REQUEST =
            new BillingClient.CreateInvoiceRequest(1L, "ST-2026-000001", 7L, new BigDecimal("2.500"));

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://billing");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final BillingClient client = new BillingClient(builder.build(), new SecurityProperties(List.of(), "key-1"));

    @Test
    void postsTheInvoiceWithTheInternalKey() {
        server.expect(requestTo(INVOICES_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(InternalApiKeyFilter.HEADER, "key-1"))
                .andExpect(content().json("""
                        {"shipmentId":1,"trackingCode":"ST-2026-000001","customerId":7,"weightKg":2.500}"""))
                .andRespond(withCreatedEntity(URI.create("/api/v1/invoices/5")));

        client.createInvoice(REQUEST);

        server.verify();
    }

    @Test
    void aFailedCallIsReportedToTheCaller() {
        server.expect(requestTo(INVOICES_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.createInvoice(REQUEST)).isInstanceOf(HttpServerErrorException.class);
    }
}
