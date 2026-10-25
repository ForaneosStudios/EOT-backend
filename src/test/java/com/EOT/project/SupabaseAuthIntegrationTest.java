package com.EOT.project;

import com.EOT.project.model.UserModel;
import com.EOT.project.model.UserRole;
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
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SupabaseAuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @MockitoBean JwtDecoder decoder;
    private final UUID userAuthId = UUID.randomUUID();
    private final UUID adminAuthId = UUID.randomUUID();
    private final UUID newAuthId = UUID.randomUUID();
    private UserModel user;

    @BeforeEach
    void setup() {
        user = users.saveAndFlush(UserModel.builder().authUserId(userAuthId).username("security_user")
                .email("security-user@example.com").build());
        users.saveAndFlush(UserModel.builder().authUserId(adminAuthId).username("security_admin")
                .email("security-admin@example.com").role(UserRole.ADMIN).build());
        when(decoder.decode("user-token")).thenReturn(jwt("user-token", userAuthId, "security-user@example.com"));
        when(decoder.decode("admin-token")).thenReturn(jwt("admin-token", adminAuthId, "security-admin@example.com"));
        when(decoder.decode("new-token")).thenReturn(jwt("new-token", newAuthId, "new@example.com"));
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid token"));
    }

    private Jwt jwt(String token, UUID id, String email) {
        return Jwt.withTokenValue(token).header("alg", "RS256").subject(id.toString())
                .claim("email", email).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    @Test
    void protectedRoutesRejectMissingOrInvalidTokens() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
    }

    @Test
    void profileIsCreatedFromVerifiedIdentityWithoutPasswordOrAdminRole() throws Exception {
        mvc.perform(post("/api/v1/users/me/profile").header("Authorization", "Bearer new-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new_profile\",\"role\":\"ADMIN\",\"email\":\"fake@example.com\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.role").value("USER")).andExpect(jsonPath("$.password").doesNotExist());
        UserModel profile = users.findByAuthUserIdAndIsDeletedFalse(newAuthId).orElseThrow();
        assertEquals(UserRole.USER, profile.getRole());
        assertNull(profile.getPassword());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer new-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("new_profile"));
    }

    @Test
    void cannotClaimExistingProfileJustByMatchingEmail() throws Exception {
        when(decoder.decode("other-token")).thenReturn(jwt("other-token", newAuthId, "security-user@example.com"));
        mvc.perform(post("/api/v1/users/me/profile").header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"other_name\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(userAuthId, users.findByEmail("security-user@example.com").orElseThrow().getAuthUserId());
    }

    @Test
    void rolesAndOwnershipAreEnforced() throws Exception {
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/user-achievements/user/" + UUID.randomUUID())
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/user-achievements/user/" + user.getUserId())
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/achievements?includeHidden=true")).andExpect(status().isForbidden());
    }

    @Test
    void cannotPostAsAnotherUserOrCreateAdminContent() throws Exception {
        String thread = "{\"categoryId\":\"" + UUID.randomUUID() + "\",\"userId\":\"" + UUID.randomUUID()
                + "\",\"title\":\"Test thread\",\"content\":\"Test content\"}";
        mvc.perform(post("/api/v1/threads").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(thread))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/categories").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/threads").header("Authorization", "Bearer new-token")
                        .contentType(MediaType.APPLICATION_JSON).content(thread))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedProfileCannotUseProtectedData() throws Exception {
        user.setIsDeleted(true);
        users.saveAndFlush(user);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
    }
}
