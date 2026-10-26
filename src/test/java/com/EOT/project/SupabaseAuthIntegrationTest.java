package com.EOT.project;

import com.EOT.project.model.UserModel;
import com.EOT.project.model.UserRole;
import com.EOT.project.repository.CategoryRepository;
import com.EOT.project.repository.AchievementRepository;
import com.EOT.project.repository.UserRepository;
import java.time.Instant;
import java.util.List;
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
    @Autowired AchievementRepository achievements;
    @MockitoBean JwtDecoder decoder;
    private final UUID userAuthId = UUID.randomUUID();
    private final UUID adminAuthId = UUID.randomUUID();
    private final UUID newAuthId = UUID.randomUUID();
    private final UUID otherAuthId = UUID.randomUUID();
    private UserModel user;
    private UserModel admin;
    private UserModel otherUser;

    @BeforeEach
    void setup() {
        user = users.saveAndFlush(UserModel.builder().authUserId(userAuthId).username("security_user")
                .email("security-user@example.com").build());
        admin = users.saveAndFlush(UserModel.builder().authUserId(adminAuthId).username("security_admin")
                .email("security-admin@example.com").role(UserRole.ADMIN).build());
        otherUser = users.saveAndFlush(UserModel.builder().authUserId(otherAuthId).username("security_other")
                .email("security-other@example.com").build());
        when(decoder.decode("user-token")).thenReturn(jwt("user-token", userAuthId, "security-user@example.com"));
        when(decoder.decode("admin-token")).thenReturn(jwt("admin-token", adminAuthId, "security-admin@example.com"));
        when(decoder.decode("new-token")).thenReturn(jwt("new-token", newAuthId, "new@example.com"));
        when(decoder.decode("other-token")).thenReturn(jwt("other-token", otherAuthId, "security-other@example.com"));
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid token"));
    }

    private Jwt jwt(String token, UUID id, String email) {
        return Jwt.withTokenValue(token).header("alg", "RS256").subject(id.toString())
                .claim("email", email).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    @Test
    void protectedRoutesRejectMissingOrInvalidTokens() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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
                .andExpect(status().isConflict());
        assertEquals(userAuthId, users.findByEmail("security-user@example.com").orElseThrow().getAuthUserId());
    }

    @Test
    void rolesAndOwnershipAreEnforced() throws Exception {
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void administratorsCanUpdateAndSoftDeleteCatalogResources() throws Exception {
        UUID categoryId = categories.findByIsDeletedFalse().getFirst().getCategoryId();
        String categoryUpdate = "{\"categoryName\":\"Categoria actualizada\","
                + "\"description\":\"Descripcion actualizada\"}";

        mvc.perform(put("/api/v1/categories/" + categoryId).header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(categoryUpdate))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/categories/" + categoryId).header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content(categoryUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Categoria actualizada"));
        mvc.perform(get("/api/v1/categories/" + categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Categoria actualizada"));
        mvc.perform(post("/api/v1/categories").header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content(categoryUpdate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(delete("/api/v1/categories/" + categoryId).header("Authorization", "Bearer admin-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        String emptyCategory = mvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"Categoria temporal\",\"description\":\"Sin hilos\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String emptyCategoryId = com.jayway.jsonpath.JsonPath.read(emptyCategory, "$.categoryId");
        mvc.perform(put("/api/v1/categories/" + categoryId).header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"Categoria temporal\",\"description\":\"Duplicada\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(delete("/api/v1/categories/" + emptyCategoryId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/categories/" + emptyCategoryId)).andExpect(status().isNotFound());

        UUID achievementId = achievements.findByIsDeletedFalse().getFirst().getAchId();
        String achievementUpdate = "{\"achName\":\"Logro actualizado\","
                + "\"achDescription\":\"Descripcion actualizada\",\"isAchHidden\":false}";
        mvc.perform(put("/api/v1/achievements/" + achievementId)
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content(achievementUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.achName").value("Logro actualizado"));
        String secondAchievement = "{\"achName\":\"Logro para detectar duplicados\","
                + "\"achDescription\":\"No permite nombres repetidos\",\"isAchHidden\":false}";
        mvc.perform(post("/api/v1/achievements").header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content(secondAchievement))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/achievements/" + achievementId)
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content(secondAchievement))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(delete("/api/v1/achievements/" + achievementId)
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/achievements/" + achievementId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/achievements/" + achievementId)).andExpect(status().isNotFound());
    }

    @Test
    void threadLifecycleEnforcesOwnershipAndAdminOnlyLocking() throws Exception {
        UUID categoryId = categories.findByIsDeletedFalse().getFirst().getCategoryId();
        String threadId = createThread(categoryId, user.getUserId(), "user-token", "Hilo editable");
        String update = "{\"title\":\"Titulo actualizado\",\"content\":\"Contenido actualizado\"}";

        mvc.perform(get("/api/v1/threads").param("categoryId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mvc.perform(put("/api/v1/threads/" + threadId).header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/threads/" + threadId).header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titulo actualizado"));

        mvc.perform(patch("/api/v1/threads/" + threadId + "/lock")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locked\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/threads/" + threadId + "/lock")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLocked").value(true));

        mvc.perform(put("/api/v1/threads/" + threadId).header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("Un hilo bloqueado solo puede ser editado por un administrador"));
        mvc.perform(put("/api/v1/threads/" + threadId).header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/threads/" + threadId).header("Authorization", "Bearer other-token"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/threads/" + threadId).header("Authorization", "Bearer user-token"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/threads/" + threadId)).andExpect(status().isNotFound());
    }

    @Test
    void postLifecycleRejectsCrossThreadRepliesAndUnauthorizedEdits() throws Exception {
        UUID categoryId = categories.findByIsDeletedFalse().getFirst().getCategoryId();
        String firstThread = createThread(categoryId, user.getUserId(), "user-token", "Primer hilo");
        String secondThread = createThread(categoryId, user.getUserId(), "user-token", "Segundo hilo");
        String parentPost = createPost(firstThread, user.getUserId(), null, "Post principal", "user-token");

        mvc.perform(get("/api/v1/posts/" + parentPost))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Post principal"));

        createPost(firstThread, user.getUserId(), parentPost, "Respuesta valida", "user-token");
        String missingParent = postJson(firstThread, user.getUserId(), UUID.randomUUID().toString(),
                "Respuesta a post inexistente");
        mvc.perform(post("/api/v1/posts").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(missingParent))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        String invalidReply = postJson(secondThread, user.getUserId(), parentPost, "Respuesta cruzada");
        mvc.perform(post("/api/v1/posts").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(invalidReply))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El post padre debe pertenecer al mismo hilo"));

        String update = "{\"content\":\"Contenido editado\"}";
        mvc.perform(put("/api/v1/posts/" + parentPost).header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/posts/" + parentPost).header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Contenido editado"));

        mvc.perform(patch("/api/v1/threads/" + firstThread + "/lock")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locked\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/posts").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postJson(firstThread, user.getUserId(), null, "No permitido")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(put("/api/v1/posts/" + parentPost).header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(put("/api/v1/posts/" + parentPost).header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/posts/" + parentPost).header("Authorization", "Bearer user-token"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/posts/" + parentPost)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/posts").param("threadId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void userAchievementCatalogIncludesMissingProgressAndValidatesUpdates() throws Exception {
        int activeAchievements = achievements.findByIsDeletedFalse().size();
        UUID achievementId = achievements.findByIsDeletedFalse().getFirst().getAchId();

        String catalog = mvc.perform(get("/api/v1/user-achievements/me")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(activeAchievements))
                .andExpect(jsonPath("$[0].progress").value(0))
                .andExpect(jsonPath("$[0].isUnlocked").value(false))
                .andReturn().getResponse().getContentAsString();
        List<String> hiddenNames = com.jayway.jsonpath.JsonPath.read(
                catalog, "$[?(@.isAchHidden == true)].achName");
        assertFalse(hiddenNames.isEmpty());
        assertTrue(hiddenNames.stream().allMatch("Logro oculto"::equals));

        String completed = progressJson(user.getUserId(), achievementId, 100);
        mvc.perform(put("/api/v1/user-achievements/progress").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(completed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(100))
                .andExpect(jsonPath("$.isUnlocked").value(true));

        String updatedCatalog = mvc.perform(get("/api/v1/user-achievements/me")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> completedProgress = com.jayway.jsonpath.JsonPath.read(
                updatedCatalog, "$[?(@.achId == '" + achievementId + "')].progress");
        assertEquals(List.of(100), completedProgress);

        String excessive = progressJson(user.getUserId(), achievementId, 101);
        mvc.perform(put("/api/v1/user-achievements/progress").header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(excessive))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(put("/api/v1/user-achievements/progress").header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON).content(completed))
                .andExpect(status().isForbidden());
    }

    private String threadJson(UUID categoryId, UUID userId, boolean locked, String title) {
        return "{\"categoryId\":\"" + categoryId + "\",\"userId\":\"" + userId
                + "\",\"title\":\"" + title + "\",\"content\":\"Contenido valido\","
                + "\"isLocked\":" + locked + "}";
    }

    private String createThread(UUID categoryId, UUID userId, String token, String title) throws Exception {
        String response = mvc.perform(post("/api/v1/threads").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(threadJson(categoryId, userId, false, title)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.threadId");
    }

    private String createPost(String threadId, UUID userId, String parentPostId, String content, String token)
            throws Exception {
        String response = mvc.perform(post("/api/v1/posts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postJson(threadId, userId, parentPostId, content)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.postId");
    }

    private String postJson(String threadId, UUID userId, String parentPostId, String content) {
        String parent = parentPostId == null ? "null" : "\"" + parentPostId + "\"";
        return "{\"threadId\":\"" + threadId + "\",\"userId\":\"" + userId
                + "\",\"parentPostId\":" + parent + ",\"content\":\"" + content + "\"}";
    }

    private String progressJson(UUID userId, UUID achievementId, int progress) {
        return "{\"userId\":\"" + userId + "\",\"achId\":\"" + achievementId
                + "\",\"progress\":" + progress + "}";
    }
}
