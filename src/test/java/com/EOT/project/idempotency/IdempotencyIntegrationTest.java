package com.EOT.project.idempotency;

import com.EOT.project.model.UserModel;
import com.EOT.project.repository.IdempotencyKeyRepository;
import com.EOT.project.repository.PostRepository;
import com.EOT.project.repository.UserRepository;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end checks of the opt-in Idempotency-Key behaviour. Not @Transactional on purpose:
 * the idempotency bookkeeping commits in its own transactions, like in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdempotencyIntegrationTest {
    /** Unlocked seed thread from data.sql. */
    private static final UUID OPEN_THREAD = UUID.fromString("20eebc99-9c0b-4ef8-bb6d-6bb9bd380a02");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired IdempotencyKeyRepository keys;
    @MockitoBean JwtDecoder decoder;

    private UserModel user;
    private UserModel other;
    private String userToken;
    private String otherToken;

    @BeforeEach
    void setup() {
        user = createUser();
        other = createUser();
        userToken = "token-" + user.getUserId();
        otherToken = "token-" + other.getUserId();
        when(decoder.decode(userToken)).thenReturn(jwt(userToken, user.getAuthUserId()));
        when(decoder.decode(otherToken)).thenReturn(jwt(otherToken, other.getAuthUserId()));
    }

    private UserModel createUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return users.saveAndFlush(UserModel.builder().authUserId(UUID.randomUUID())
                .username("idem_" + suffix).email("idem-" + suffix + "@example.com").build());
    }

    private Jwt jwt(String token, UUID subject) {
        return Jwt.withTokenValue(token).header("alg", "RS256").subject(subject.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    private MockHttpServletRequestBuilder createPost(String token, UUID userId, String content) {
        return post("/api/v1/posts").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"threadId\":\"" + OPEN_THREAD + "\",\"userId\":\"" + userId
                        + "\",\"content\":\"" + content + "\"}");
    }

    private long postsWithContent(String content) {
        return posts.findByThread_ThreadIdAndIsDeletedFalseOrderByCreatedAtAsc(OPEN_THREAD).stream()
                .filter(p -> content.equals(p.getContent())).count();
    }

    @Test
    void requestsWithoutHeaderBehaveExactlyAsBefore() throws Exception {
        String content = "sin-llave-" + UUID.randomUUID();
        mvc.perform(createPost(userToken, user.getUserId(), content)).andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER));
        mvc.perform(createPost(userToken, user.getUserId(), content)).andExpect(status().isCreated());
        assertEquals(2, postsWithContent(content), "legacy clients keep creating one post per call");
    }

    @Test
    void retryWithSameKeyReplaysStoredResponseWithoutCreatingDuplicate() throws Exception {
        String content = "con-llave-" + UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String first = mvc.perform(createPost(userToken, user.getUserId(), content)
                        .header(IdempotencyFilter.KEY_HEADER, key))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER))
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(createPost(userToken, user.getUserId(), content)
                        .header(IdempotencyFilter.KEY_HEADER, key))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        assertEquals(first, second);
        assertEquals(1, postsWithContent(content));
    }

    @Test
    void reusingKeyForDifferentRequestIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        mvc.perform(createPost(userToken, user.getUserId(), "a-" + UUID.randomUUID())
                .header(IdempotencyFilter.KEY_HEADER, key)).andExpect(status().isCreated());
        String other = "b-" + UUID.randomUUID();
        mvc.perform(createPost(userToken, user.getUserId(), other)
                        .header(IdempotencyFilter.KEY_HEADER, key))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertEquals(0, postsWithContent(other));
    }

    @Test
    void keysAreScopedPerUser() throws Exception {
        String key = UUID.randomUUID().toString();
        String content = "por-usuario-" + UUID.randomUUID();
        mvc.perform(createPost(userToken, user.getUserId(), content)
                .header(IdempotencyFilter.KEY_HEADER, key)).andExpect(status().isCreated());
        mvc.perform(createPost(otherToken, other.getUserId(), content)
                        .header(IdempotencyFilter.KEY_HEADER, key))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER));
        assertEquals(2, postsWithContent(content));
    }

    @Test
    void malformedKeyIsRejectedBeforeRunningRequest() throws Exception {
        String content = "llave-mala-" + UUID.randomUUID();
        mvc.perform(createPost(userToken, user.getUserId(), content)
                        .header(IdempotencyFilter.KEY_HEADER, "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_INVALID"));
        assertEquals(0, postsWithContent(content));
    }

    @Test
    void forbiddenResponsesAreNotStoredSoALaterRetryRunsAgain() throws Exception {
        String key = UUID.randomUUID().toString();
        String content = "ajeno-" + UUID.randomUUID();
        // Posting on behalf of another user is rejected with 403 by the controller.
        mvc.perform(createPost(userToken, other.getUserId(), content)
                .header(IdempotencyFilter.KEY_HEADER, key)).andExpect(status().isForbidden());
        assertTrue(keys.findByAuthSubAndIdemKey(user.getAuthUserId().toString(), key).isEmpty());
    }

    @Test
    void unauthenticatedRequestsDoNotConsumeKeys() throws Exception {
        String key = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/posts").header(IdempotencyFilter.KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertEquals(0, keys.findAll().stream().filter(k -> key.equals(k.getIdemKey())).count());
    }

    @Test
    void readsIgnoreTheHeader() throws Exception {
        mvc.perform(get("/api/v1/categories").header(IdempotencyFilter.KEY_HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER));
    }
}
