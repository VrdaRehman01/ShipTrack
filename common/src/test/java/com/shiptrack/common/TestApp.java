package com.shiptrack.common;

import com.shiptrack.common.error.ApiException;
import com.shiptrack.common.security.AuthenticatedUser;
import com.shiptrack.common.security.CurrentUser;
import com.shiptrack.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;

/** A tiny app that exercises everything shiptrack-common auto-configures. */
@SpringBootApplication
public class TestApp {

    record Body(@NotBlank String name) {
    }

    @RestController
    static class TestController {

        @GetMapping("/api/test/me")
        Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser user) {
            return Map.of("id", user.id(), "role", user.role(), "hasToken", CurrentUser.bearerToken().isPresent(),
                    "correlationId", MDC.get("correlationId"));
        }

        @GetMapping("/api/test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String admin() {
            return "ok";
        }

        @GetMapping("/api/test/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/api/test/not-found")
        String notFound() {
            throw ApiException.notFound("Thing", 7);
        }

        @GetMapping("/api/test/lock")
        String lock() {
            throw new OptimisticLockingFailureException("v");
        }

        @GetMapping("/api/test/integrity")
        String integrity() {
            throw new DataIntegrityViolationException("dup");
        }

        @GetMapping("/api/test/downstream")
        String downstream() {
            throw new ResourceAccessException("connection refused");
        }

        @PostMapping("/api/test/validate")
        String validate(@Valid @RequestBody Body body) {
            return body.name();
        }

        @GetMapping("/api/test/page")
        PageResponse<String> page(@RequestParam(defaultValue = "2") @Max(10) int size) {
            return PageResponse.from(new PageImpl<>(List.of(1, 2), PageRequest.of(0, size), 5), i -> "item-" + i);
        }

        @GetMapping("/api/test/number")
        int number(@RequestParam int value) {
            return value;
        }

        @GetMapping("/public/hello")
        String hello() {
            return "hello";
        }

        @GetMapping("/internal/ping")
        String ping() {
            return "pong";
        }
    }
}
