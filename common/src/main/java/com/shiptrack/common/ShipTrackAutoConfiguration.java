package com.shiptrack.common;

import com.shiptrack.common.error.GlobalExceptionHandler;
import com.shiptrack.common.security.InternalApiKeyFilter;
import com.shiptrack.common.security.JwtAuthenticationFilter;
import com.shiptrack.common.security.JwtProperties;
import com.shiptrack.common.security.JwtVerifier;
import com.shiptrack.common.security.SecurityProperties;
import com.shiptrack.common.web.CorrelationIdFilter;
import com.shiptrack.common.web.CorrelationIdInterceptor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Everything a ShipTrack Spring service gets just by depending on shiptrack-common:
 * stateless JWT security, /internal/** key auth, correlation IDs, the standard error shape and Swagger with a Bearer button.
 */
@AutoConfiguration(before = {
    ServletWebSecurityAutoConfiguration.class,
    ManagementWebSecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class})
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, SecurityProperties.class})
public class ShipTrackAutoConfiguration {

    public static final String BEARER_SCHEME = "bearerAuth";
    public static final String CORS_SOURCE_BEAN = "corsConfigurationSource";

    /** Always open on every service. */
    private static final String[] ALWAYS_PUBLIC = {
        "/actuator/health/**",
        "/actuator/info",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/error"
    };

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    JwtVerifier jwtVerifier(JwtProperties properties, Clock clock) {
        return new JwtVerifier(properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE); // before Spring Security
        return registration;
    }

    @Bean
    CorrelationIdInterceptor correlationIdInterceptor() {
        return new CorrelationIdInterceptor();
    }

    /** We only authenticate with JWTs; this also stops Boot creating a default user with a logged password. */
    @Bean
    @ConditionalOnMissingBean
    AuthenticationManager jwtOnlyAuthenticationManager() {
        return authentication -> {
            throw new ProviderNotFoundException("ShipTrack services authenticate with JWTs only");
        };
    }

    /**
     * 401/403 raised in the filter chain go through GlobalExceptionHandler via the exception resolver,
     * so they share the standard error shape. Role checks live on controllers with @PreAuthorize.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain shipTrackSecurityFilterChain(
            HttpSecurity http,
            JwtVerifier jwtVerifier,
            SecurityProperties securityProperties,
            BeanFactory beanFactory,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        String[] publicPaths = securityProperties.publicPaths().toArray(String[]::new);
        // CORS only where a service defines a "corsConfigurationSource" bean (the gateway); browsers never
        // call services directly. Looked up by name: Spring MVC's introspector is a CorsConfigurationSource too.
        if (beanFactory.containsBean(CORS_SOURCE_BEAN)) {
            CorsConfigurationSource cors = beanFactory.getBean(CORS_SOURCE_BEAN, CorsConfigurationSource.class);
            http.cors(c -> c.configurationSource(cors));
        }
        return http
                .csrf(AbstractHttpConfigurer::disable) // stateless JWT API, no cookies
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers(ALWAYS_PUBLIC).permitAll();
                    if (publicPaths.length > 0) {
                        auth.requestMatchers(publicPaths).permitAll();
                    }
                    auth.requestMatchers(InternalApiKeyFilter.INTERNAL_PATHS)
                            .hasAuthority(InternalApiKeyFilter.INTERNAL_AUTHORITY);
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> exceptionResolver.resolveException(req, res, null, e))
                        .accessDeniedHandler((req, res, e) -> exceptionResolver.resolveException(req, res, null, e)))
                .addFilterBefore(new InternalApiKeyFilter(securityProperties.internalApiKey()),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtVerifier), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    OpenAPI shipTrackOpenApi(@Value("${spring.application.name:shiptrack}") String appName) {
        return new OpenAPI()
                .info(new Info()
                        .title("ShipTrack " + appName)
                        .version("v1")
                        .description("Log in via auth-service, copy accessToken, then click Authorize."))
                // Relative: Swagger UI calls whatever address it was opened on (localhost, http or CloudFront https),
                // never a service's internal Docker hostname.
                .servers(List.of(new Server().url("/").description("This site")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
