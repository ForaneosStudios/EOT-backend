package com.EOT.project.concurrency;

import com.EOT.project.idempotency.IdempotencyFilter;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** "Last edit made wins" for clients that send X-Client-Intent-At; unchanged for everyone else. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaleWriteIntegrationTest {
    private static final UUID OPEN_THREAD = UUID.fromString("20eebc99-9c0b-4ef8-bb6d-6bb9bd380a02");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @MockitoBean JwtDecoder decoder;

    private UserModel user;
    private String token;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = users.saveAndFlush(UserModel.builder().authUserId(UUID.randomUUID())
                .username("stale_" + suffix).email("stale-" + suffix + "@example.com").build());
        token = "token-" + user.getUserId();
        when(decoder.decode(token)).thenReturn(Jwt.withTokenValue(token).header("alg", "RS256")
                .subject(user.getAuthUserId().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300)).build());
    }

    private String createPost(String content) throws Exception {
        String body = mvc.perform(post("/api/v1/posts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"threadId\":\"" + OPEN_THREAD + "\",\"userId\":\"" + user.getUserId()
                                + "\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.postId");
    }

    private String createThread() throws Exception {
        String body = mvc.perform(post("/api/v1/threads").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user.getUserId() + "\",\"title\":\"Titulo original\",\"content\":\"Contenido original\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.threadId");
    }

    private String editPost(String postId, String content, Instant intent) throws Exception {
        var request = put("/api/v1/posts/" + postId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + content + "\"}");
        if (intent != null) request.header(ClientIntent.HEADER, intent.toString());
        return mvc.perform(request).andReturn().getResponse().getContentAsString();
    }

    @Test
    void olderEditIsRejectedAndNewerEditWins() throws Exception {
        String postId = createPost("original");
        Instant later = Instant.now();
        mvc.perform(put("/api/v1/posts/" + postId).header("Authorization", "Bearer " + token)
                        .header(ClientIntent.HEADER, later.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"B (laptop)\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("B (laptop)"));

        // The phone's edit was made two hours earlier and arrives now: it must not overwrite B.
        mvc.perform(put("/api/v1/posts/" + postId).header("Authorization", "Bearer " + token)
                        .header(ClientIntent.HEADER, later.minus(2, ChronoUnit.HOURS).toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"A (telefono)\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_WRITE"))
                .andExpect(jsonPath("$.current.content").value("B (laptop)"));

        mvc.perform(get("/api/v1/posts/" + postId)).andExpect(jsonPath("$.content").value("B (laptop)"));
    }

    @Test
    void requestsWithoutHeaderKeepLastArrivalWins() throws Exception {
        String postId = createPost("original");
        editPost(postId, "nuevo", Instant.now());
        mvc.perform(put("/api/v1/posts/" + postId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"legado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("legado"));
    }

    @Test
    void futureIntentIsCappedAtNowInsteadOfWinningForever() throws Exception {
        String postId = createPost("original");
        editPost(postId, "desde el futuro", Instant.now().plus(365, ChronoUnit.DAYS));
        // An edit made right now must still be able to replace it.
        mvc.perform(put("/api/v1/posts/" + postId).header("Authorization", "Bearer " + token)
                        .header(ClientIntent.HEADER, Instant.now().plusSeconds(1).toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"ahora\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("ahora"));
    }

    @Test
    void malformedIntentIsABadRequest() throws Exception {
        String postId = createPost("original");
        mvc.perform(put("/api/v1/posts/" + postId).header("Authorization", "Bearer " + token)
                        .header(ClientIntent.HEADER, "ayer por la tarde")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void staleThreadEditAndLockAreRejected() throws Exception {
        String threadId = createThread();
        String old = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        mvc.perform(put("/api/v1/threads/" + threadId).header("Authorization", "Bearer " + token)
                        .header(ClientIntent.HEADER, old).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Titulo viejo\",\"content\":\"Contenido viejo\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.current.title").value("Titulo original"));
        mvc.perform(put("/api/v1/threads/" + threadId).header("Authorization", "Bearer " + token)
                        .header(ClientIntent.HEADER, Instant.now().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Titulo nuevo\",\"content\":\"Contenido nuevo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Titulo nuevo"));
    }

    @Test
    void staleAvatarChangeIsRejected() throws Exception {
        mvc.perform(patch("/api/v1/users/me/profile-picture").header("Authorization", "Bearer " + token)
                        .header(ClientIntent.HEADER, Instant.now().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profilePicture\":\"/avatars/avatar-2.svg\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/users/me/profile-picture").header("Authorization", "Bearer " + token)
                        .header(ClientIntent.HEADER, Instant.now().minus(1, ChronoUnit.HOURS).toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"profilePicture\":\"/avatars/avatar-3.svg\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.current.profilePicture").value("/avatars/avatar-2.svg"));
    }

    @Test
    void staleResultIsReplayedForTheSameIdempotencyKey() throws Exception {
        String postId = createPost("original");
        editPost(postId, "B", Instant.now());
        String key = UUID.randomUUID().toString();
        String old = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(put("/api/v1/posts/" + postId).header("Authorization", "Bearer " + token)
                            .header(IdempotencyFilter.KEY_HEADER, key).header(ClientIntent.HEADER, old)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"A\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        }
        mvc.perform(get("/api/v1/posts/" + postId)).andExpect(jsonPath("$.content").value("B"));
    }
}
