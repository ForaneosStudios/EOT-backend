package com.EOT.project.controller;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Alice owns a thread, Bob posts in it, and Alice reports and reviews. */
class PostReportApiTest extends ApiIntegrationTestSupport {
    private String threadId;
    private String bobPostId;
    private String alicePostId;

    private String created(String path, String token, String body, String field) throws Exception {
        String response = mvc.perform(post(path).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$." + field);
    }

    @BeforeEach
    void createThreadAndPosts() throws Exception {
        threadId = created("/api/v1/threads", aliceToken, "{\"userId\":\"" + alice.getUserId()
                + "\",\"title\":\"Hilo de reportes\",\"content\":\"Contenido del hilo\"}", "threadId");
        bobPostId = created("/api/v1/posts", bobToken, "{\"threadId\":\"" + threadId + "\",\"userId\":\""
                + bob.getUserId() + "\",\"content\":\"Mensaje de Bob\"}", "postId");
        alicePostId = created("/api/v1/posts", aliceToken, "{\"threadId\":\"" + threadId + "\",\"userId\":\""
                + alice.getUserId() + "\",\"content\":\"Mensaje de Alice\"}", "postId");
    }

    private String report(String token, String postId, String reason) throws Exception {
        return created("/api/v1/posts/" + postId + "/reports", token, "{\"reason\":\"" + reason + "\"}", "reportId");
    }

    @Test
    void threadAuthorReviewsReportsAboutTheirThread() throws Exception {
        String reportId = report(aliceToken, bobPostId, "  Spam  ");

        mvc.perform(get("/api/v1/threads/" + threadId + "/reports").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].reason").value("Spam"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].reporterUsername").value(alice.getUsername()));

        mvc.perform(patch("/api/v1/post-reports/" + reportId).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.reviewedAt").exists());
    }

    @Test
    void cannotReportYourOwnPostOrAMissingOne() throws Exception {
        mvc.perform(post("/api/v1/posts/" + alicePostId + "/reports").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Yo\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/posts/" + UUID.randomUUID() + "/reports").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Nada\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/posts/" + bobPostId + "/reports").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheThreadAuthorCanSeeOrReviewReports() throws Exception {
        String reportId = report(aliceToken, bobPostId, "Spam");
        mvc.perform(get("/api/v1/threads/" + threadId + "/reports").header("Authorization", bearer(bobToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/post-reports/" + reportId).header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/threads/" + UUID.randomUUID() + "/reports").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/post-reports/" + UUID.randomUUID()).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/post-reports/" + reportId).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
