package com.EOT.project;

import com.EOT.project.model.UserModel;
import com.EOT.project.model.UserRole;
import com.EOT.project.repository.CategoryRepository;
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
    @Autowired CategoryRepository categories;
    @MockitoBean JwtDecoder decoder;
    private final UUID userAuthId = UUID.randomUUID();
    private final UUID adminAuthId = UUID.randomUUID();
    private final UUID newAuthId = UUID.randomUUID();
    private UserModel user;
    private UserModel admin;

    @BeforeEach
    void setup() {
        user = users.saveAndFlush(UserModel.builder().authUserId(userAuthId).username("security_user")
                .email("security-user@example.com").build());
        admin = users.saveAndFlush(UserModel.builder().authUserId(adminAuthId).username("security_admin")
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

    @Test
    void threadRoutesAcceptOwnersAndAdminsWhileEnforcingValidation() throws Exception {
        UUID categoryId = categories.findByIsDeletedFalse().getFirst().getCategoryId();
        String userThread = threadJson(categoryId, user.getUserId(), false, "Hilo del usuario");

        mvc.perform(post("/api/v1/threads").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(userThread))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Hilo del usuario"))
                .andExpect(jsonPath("$.userId").value(user.getUserId().toString()))
                .andExpect(jsonPath("$.isLocked").value(false));

        String lockedByUser = threadJson(categoryId, user.getUserId(), true, "Bloqueado por usuario");
        mvc.perform(post("/api/v1/threads").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(lockedByUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Solo un administrador puede bloquear un hilo"));

        String lockedByAdmin = threadJson(categoryId, admin.getUserId(), true, "Aviso administrativo");
        mvc.perform(post("/api/v1/threads").header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content(lockedByAdmin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isLocked").value(true));

        String invalidThread = threadJson(categoryId, user.getUserId(), false, "");
        mvc.perform(post("/api/v1/threads").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(invalidThread))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String missingCategory = threadJson(UUID.randomUUID(), user.getUserId(), false, "Sin categoria");
        mvc.perform(post("/api/v1/threads").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(missingCategory))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void achievementRoutesSeparatePublicDataFromAdministrativeOperations() throws Exception {
        mvc.perform(get("/api/v1/achievements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].achId").exists());

        String achievement = "{\"achName\":\"Prueba de integracion\","
                + "\"achDescription\":\"Logro creado por una prueba real\",\"isAchHidden\":true}";
        mvc.perform(post("/api/v1/achievements").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(achievement))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/achievements").header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String response = mvc.perform(post("/api/v1/achievements")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content(achievement))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isAchHidden").value(true))
                .andReturn().getResponse().getContentAsString();
        String achievementId = com.jayway.jsonpath.JsonPath.read(response, "$.achId");

        mvc.perform(get("/api/v1/achievements/" + achievementId))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/achievements/" + achievementId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.achName").value("Prueba de integracion"));

        mvc.perform(post("/api/v1/achievements").header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content(achievement))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    private String threadJson(UUID categoryId, UUID userId, boolean locked, String title) {
        return "{\"categoryId\":\"" + categoryId + "\",\"userId\":\"" + userId
                + "\",\"title\":\"" + title + "\",\"content\":\"Contenido valido\","
                + "\"isLocked\":" + locked + "}";
    }
}
