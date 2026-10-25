package com.EOT.project.config;

import com.EOT.project.repository.UserRepository;
import java.util.List;
import java.util.Arrays;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(@Value("${SUPABASE_AUTH_ISSUER:}") String issuer) {
        if (issuer.isBlank()) {
            return token -> { throw new BadJwtException("Supabase Auth issuer is not configured"); };
        }
        String normalizedIssuer = issuer.replaceAll("/+$", "");
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users, JwtDecoder decoder) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Stateless Bearer tokens, not browser cookie authentication.
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories/**", "/api/v1/threads/**",
                        "/api/v1/posts/**", "/api/v1/achievements/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/categories/**", "/api/v1/achievements/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/users/me/profile").authenticated()
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(token -> {
                UUID authId;
                try {
                    authId = UUID.fromString(token.getSubject());
                } catch (RuntimeException ex) {
                    throw new BadCredentialsException("Invalid subject");
                }
                var authorities = users.findByAuthUserIdAndIsDeletedFalse(authId)
                        .map(user -> List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                        .orElseGet(List::of);
                return new JwtAuthenticationToken(token, authorities, authId.toString());
            })));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${APP_CORS_ALLOWED_ORIGINS:}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim)
                .filter(origin -> !origin.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
