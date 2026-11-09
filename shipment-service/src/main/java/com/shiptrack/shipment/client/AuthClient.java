package com.shiptrack.shipment.client;

import com.shiptrack.common.error.ApiException;
import com.shiptrack.common.security.CurrentUser;
import com.shiptrack.common.security.Role;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Looks users up in auth-service, forwarding the caller's own JWT (an admin assigning a driver),
 * so auth-service applies its normal permission checks. No extra trust between services is needed.
 */
@Component
public class AuthClient {

    public record UserView(Long id, String email, String fullName, Role role, String phone) {
    }

    private final RestClient restClient;

    public AuthClient(@Qualifier("authRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** Empty if the user doesn't exist. */
    public Optional<UserView> findUser(Long userId) {
        return fetch("/api/v1/users/" + userId);
    }

    /** The logged-in caller's own details (anyone may read their own). */
    public UserView currentUser() {
        return fetch("/api/v1/users/me").orElseThrow(() -> ApiException.forbidden("Your account no longer exists"));
    }

    private Optional<UserView> fetch(String path) {
        String token = CurrentUser.bearerToken().orElseThrow(() -> ApiException.forbidden("No caller token to forward"));
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(UserView.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized e) {
            throw ApiException.forbidden("Only admins can look up drivers");
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw ApiException.dependencyUnavailable("auth-service", e);
        }
    }
}
