package com.EOT.project.controller;

import com.EOT.project.model.UserModel;
import com.EOT.project.model.UserRole;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ThreadAndUserApiTest extends ApiIntegrationTestSupport {
    private String adminToken;
    private String threadId;

    @BeforeEach
    void createAdminAndThread() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserModel admin = users.saveAndFlush(UserModel.builder().authUserId(UUID.randomUUID()).role(UserRole.ADMIN)
                .username("admin_" + suffix).email("admin-" + suffix + "@example.com").build());
        adminToken = tokenFor(admin);
        String body = mvc.perform(post("/api/v1/threads").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + alice.getUserId() + "\",\"title\":\"Estrategias de jefe\",\"content\":\"Guía completa\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        threadId = JsonPath.read(body, "$.threadId");
    }

    @Test
    void threadsAreListedSearchedAndFilteredByCategory() throws Exception {
        mvc.perform(get("/api/v1/threads")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.threadId=='" + threadId + "')]").exists());

        String categoryId = JsonPath.read(mvc.perform(get("/api/v1/threads/" + threadId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.categoryId");
        mvc.perform(get("/api/v1/threads").param("categoryId", categoryId)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.threadId=='" + threadId + "')]").exists());
        mvc.perform(get("/api/v1/threads").param("categoryId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/threads/search").param("title", "jefe").param("author", alice.getUsername()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].threadId").value(threadId));
        mvc.perform(get("/api/v1/threads/search").param("participantId", alice.getUserId().toString()).param("size", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(50));
        mvc.perform(get("/api/v1/threads/search").param("title", "no-existe-" + UUID.randomUUID()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void onlyOwnersAndAdminsChangeOrDeleteAThread() throws Exception {
        mvc.perform(put("/api/v1/threads/" + threadId).header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Titulo robado\",\"content\":\"Otro texto\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/threads/" + threadId).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Titulo nuevo\",\"content\":\"Texto nuevo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Titulo nuevo"));

        // Only an admin can lock, and a locked thread can then only be edited by an admin.
        mvc.perform(patch("/api/v1/threads/" + threadId + "/lock").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locked\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/threads/" + threadId + "/lock").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locked\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isLocked").value(true));
        mvc.perform(put("/api/v1/threads/" + threadId).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Titulo bloqueado\",\"content\":\"Texto\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/threads/" + threadId).header("Authorization", bearer(bobToken)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/threads/" + threadId).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/threads/" + threadId)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/threads/" + threadId).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void usernameAvailabilityIsPublicAndValidated() throws Exception {
        mvc.perform(get("/api/v1/users/availability").param("username", alice.getUsername()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/v1/users/availability").param("username", "libre_" + UUID.randomUUID().toString().substring(0, 8)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        mvc.perform(get("/api/v1/users/availability").param("username", "no valido!"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/availability")).andExpect(status().isBadRequest());
    }

    @Test
    void playersSeeTheirOwnProfileAndOnlyAdminsManageAccounts() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(alice.getUsername()))
                .andExpect(jsonPath("$.role").value("USER")).andExpect(jsonPath("$.password").doesNotExist());

        mvc.perform(get("/api/v1/users/" + bob.getUserId()).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/" + bob.getUserId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(bob.getUsername()));
        mvc.perform(get("/api/v1/users/" + UUID.randomUUID()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/users/" + bob.getUserId()).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/users/" + bob.getUserId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/users/" + UUID.randomUUID()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }
}
