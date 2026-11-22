package com.EOT.project.config;

import com.EOT.project.idempotency.IdempotencyFilter;
import com.EOT.project.idempotency.IdempotencyService;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(@Value("${SUPABASE_AUTH_ISSUER:}") String issuer) {
        if (issuer.isBlank()) {
            return token -> { throw new BadJwtException("Supabase Auth issuer is not configured"); };
        }
        String normalizedIssuer = trimTrailingSlashes(issuer);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(normalizedIssuer + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null
                && jwt.getAudience().contains("authenticated") && jwt.getExpiresAt() != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience or expiration", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(normalizedIssuer), audience));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users, JwtDecoder decoder,
                                            ApiSecurityErrorHandler securityErrorHandler,
                                            IdempotencyService idempotencyService, ObjectMapper objectMapper,
                                            PlatformTransactionManager transactionManager) {
        http
            // Opt-in: only requests that send Idempotency-Key are affected. Runs after
            // authorization so keys are scoped per user and rejected requests never use one.
            .addFilterAfter(new IdempotencyFilter(idempotencyService, objectMapper, transactionManager), AuthorizationFilter.class)
            // API clients authenticate with an explicit Bearer token, never with
            // automatically attached browser cookies. Keep CSRF enabled by default
            // and ignore only the stateless API and the local H2 console.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/h2-console/**"))
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
            .exceptionHandling(errors -> errors
                    .authenticationEntryPoint(securityErrorHandler)
                    .accessDeniedHandler(securityErrorHandler))
            .authorizeHttpRequests(auth -> auth
                // Public liveness/readiness endpoint. It reveals only UP/DOWN.
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories/**", "/api/v1/threads/**",
                        "/api/v1/posts/**", "/api/v1/achievements/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/availability").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/confirm").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/password-reset/validate").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/categories/**", "/api/v1/achievements/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/categories/**", "/api/v1/achievements/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/categories/**", "/api/v1/achievements/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/threads/*/lock").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/users/me/profile").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me/profile-picture").authenticated()
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                    .authenticationEntryPoint(securityErrorHandler)
                    .accessDeniedHandler(securityErrorHandler)
                    .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(token -> {
                UUID authId;
                try {
                    authId = UUID.fromString(token.getSubject());
                } catch (IllegalArgumentException ex) {
                    throw new BadCredentialsException("Invalid subject", ex);
                }
                var profile = users.findByAuthUserIdAndIsDeletedFalse(authId);
                // A password reset closes every session: tokens issued before it are dead.
                profile.map(UserModel::getSessionsValidAfter).ifPresent(cutoff -> {
                    if (token.getIssuedAt() == null || token.getIssuedAt().isBefore(cutoff)) {
                        throw new BadCredentialsException("Session closed by a password reset");
                    }
                });
                var authorities = profile
                        .map(user -> List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                        .orElseGet(List::of);
                return new JwtAuthenticationToken(token, authorities, authId.toString());
            })));
        return http.build();
    }

    private static String trimTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // API authentication uses explicitly supplied Bearer tokens rather than
        // cookies, so any browser origin may call the backend at the CORS layer.
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Lets browser clients read the idempotency replay marker and retry hints.
        // Date lets browser clients estimate their clock offset for X-Client-Intent-At.
        config.setExposedHeaders(List.of(IdempotencyFilter.REPLAYED_HEADER, "Retry-After", "Location", "Date"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
