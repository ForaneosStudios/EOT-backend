package com.EOT.project.realtime;

import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Not transactional on purpose: live events go out after the commit, as in production.
 * The players created here are removed afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveUpdatesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @MockitoBean JwtDecoder decoder;

    UserModel player;
    UserModel rival;

    @BeforeEach
    void createPlayers() {
        player = create("live");
        rival = create("rival");
    }

    @AfterEach
    void removePlayers() {
        users.deleteAll(users.findAllById(java.util.List.of(player.getUserId(), rival.getUserId())));
    }

    private UserModel create(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserModel user = users.saveAndFlush(UserModel.builder().authUserId(UUID.randomUUID())
                .username(prefix + "_" + suffix).email(prefix + "-" + suffix + "@example.com").build());
        when(decoder.decode("token-" + user.getUserId())).thenReturn(Jwt.withTokenValue("token-" + user.getUserId())
                .header("alg", "RS256").subject(user.getAuthUserId().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());
        return user;
    }

    private static String bearer(UserModel user) {
        return "Bearer token-" + user.getUserId();
    }

    @Test
    void theStreamRequiresASession() throws Exception {
        mvc.perform(get("/api/v1/live/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void aNewRecordFromTheGameReachesTheOpenStreams() throws Exception {
        MvcResult playerStream = mvc.perform(get("/api/v1/live/me").header("Authorization", bearer(player))
                .accept(MediaType.TEXT_EVENT_STREAM)).andExpect(request().asyncStarted()).andReturn();
        MvcResult rivalStream = mvc.perform(get("/api/v1/live/me").header("Authorization", bearer(rival))
                .accept(MediaType.TEXT_EVENT_STREAM)).andExpect(request().asyncStarted()).andReturn();
        assertTrue(playerStream.getResponse().getContentAsString().contains("event:ready"));

        // The game submits a run for the player.
        mvc.perform(post("/api/v1/time-records").header("Authorization", bearer(player))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"timeMs\":61000}"))
                .andExpect(status().isOk());

        String playerEvents = playerStream.getResponse().getContentAsString();
        assertTrue(playerEvents.contains("event:record"), playerEvents);
        assertTrue(playerEvents.contains("\"bestTimeMs\":61000"), playerEvents);
        assertTrue(playerEvents.contains("event:leaderboard"), playerEvents);
        String rivalEvents = rivalStream.getResponse().getContentAsString();
        assertFalse(rivalEvents.contains("event:record"), rivalEvents);
        assertTrue(rivalEvents.contains("event:leaderboard"), rivalEvents);

        // A slower run changes nothing, so nothing is pushed.
        int before = playerStream.getResponse().getContentAsString().length();
        mvc.perform(post("/api/v1/time-records").header("Authorization", bearer(player))
                .contentType(MediaType.APPLICATION_JSON).content("{\"timeMs\":99000}")).andExpect(status().isOk());
        assertEquals(before, playerStream.getResponse().getContentAsString().length());
    }

    @Test
    void theLeaderboardIsPublicOrderedAndHidesEmails() throws Exception {
        player.setBestTimeMs(1L);
        rival.setBestTimeMs(2L);
        users.saveAllAndFlush(java.util.List.of(player, rival));

        mvc.perform(get("/api/v1/leaderboard").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].username").value(player.getUsername()))
                .andExpect(jsonPath("$[0].bestTimeMs").value(1))
                .andExpect(jsonPath("$[1].username").value(rival.getUsername()))
                .andExpect(jsonPath("$[0].email").doesNotExist());
        mvc.perform(get("/api/v1/leaderboard").param("limit", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(50)));
        mvc.perform(get("/api/v1/leaderboard").param("limit", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
}
