package com.EOT.project.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GameTimeRecordApiTest extends ApiIntegrationTestSupport {

    private org.springframework.test.web.servlet.ResultActions submit(String method, String token, String body) throws Exception {
        var request = "PUT".equals(method) ? put("/api/v1/time-records/me") : post("/api/v1/time-records");
        return mvc.perform(request.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/time-records/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/time-records").contentType(MediaType.APPLICATION_JSON).content("{\"timeMs\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startsWithoutARecord() throws Exception {
        mvc.perform(get("/api/v1/time-records/me").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(alice.getUserId().toString()))
                .andExpect(jsonPath("$.timeMs").doesNotExist());
    }

    @Test
    void keepsOnlyTheBestTime() throws Exception {
        submit("POST", aliceToken, "{\"timeMs\":90000}").andExpect(status().isOk()).andExpect(jsonPath("$.timeMs").value(90000));
        // A slower run does not replace it, whichever verb the client uses.
        submit("POST", aliceToken, "{\"timeMs\":120000}").andExpect(status().isOk()).andExpect(jsonPath("$.timeMs").value(90000));
        submit("PUT", aliceToken, "{\"timeMs\":95000}").andExpect(status().isOk()).andExpect(jsonPath("$.timeMs").value(90000));
        submit("PUT", aliceToken, "{\"timeMs\":80000}").andExpect(status().isOk()).andExpect(jsonPath("$.timeMs").value(80000));
        mvc.perform(get("/api/v1/time-records/me").header("Authorization", bearer(aliceToken)))
                .andExpect(jsonPath("$.timeMs").value(80000));
    }

    @Test
    void playersDoNotSeeEachOthersRecords() throws Exception {
        submit("POST", aliceToken, "{\"timeMs\":50000}").andExpect(status().isOk());
        mvc.perform(get("/api/v1/time-records/me").header("Authorization", bearer(bobToken)))
                .andExpect(jsonPath("$.timeMs").doesNotExist());
    }

    @Test
    void rejectsInvalidTimes() throws Exception {
        submit("POST", aliceToken, "{\"timeMs\":0}").andExpect(status().isBadRequest());
        submit("POST", aliceToken, "{\"timeMs\":-5}").andExpect(status().isBadRequest());
        submit("POST", aliceToken, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        submit("PUT", aliceToken, "{\"timeMs\":\"rapido\"}").andExpect(status().isBadRequest());
    }
}
