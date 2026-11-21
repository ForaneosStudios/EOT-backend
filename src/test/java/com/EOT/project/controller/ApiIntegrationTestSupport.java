package com.EOT.project.controller;

import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.when;

/**
 * Shared setup for end-to-end API tests: a real Spring context on H2 with the security chain on,
 * and two players ("alice" and "bob") that can authenticate with a bearer token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class ApiIntegrationTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository users;
    @MockitoBean protected JwtDecoder decoder;

    protected UserModel alice;
    protected UserModel bob;
    protected String aliceToken;
    protected String bobToken;

    @BeforeEach
    void createPlayers() {
        alice = createPlayer("alice");
        bob = createPlayer("bob");
        aliceToken = tokenFor(alice);
        bobToken = tokenFor(bob);
    }

    protected UserModel createPlayer(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return users.saveAndFlush(UserModel.builder().authUserId(UUID.randomUUID())
                .username(prefix + "_" + suffix).email(prefix + "-" + suffix + "@example.com").build());
    }

    protected String tokenFor(UserModel user) {
        String token = "token-" + user.getUserId();
        when(decoder.decode(token)).thenReturn(Jwt.withTokenValue(token).header("alg", "RS256")
                .subject(user.getAuthUserId().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300)).build());
        return token;
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
