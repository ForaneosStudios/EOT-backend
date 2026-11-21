package com.EOT.project.controller;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PrivateConversationApiTest extends ApiIntegrationTestSupport {

    private String startConversation(String token, Object recipientId) throws Exception {
        String body = mvc.perform(post("/api/v1/private-conversations").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipientId\":\"" + recipientId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.conversationId");
    }

    private void send(String token, String conversationId, String content) throws Exception {
        mvc.perform(post("/api/v1/private-conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/private-conversations")).andExpect(status().isUnauthorized());
    }

    @Test
    void startingTheSameChatTwiceReturnsTheSameConversation() throws Exception {
        String first = startConversation(aliceToken, bob.getUserId());
        String again = startConversation(aliceToken, bob.getUserId());
        String fromBob = startConversation(bobToken, alice.getUserId());
        org.junit.jupiter.api.Assertions.assertEquals(first, again);
        org.junit.jupiter.api.Assertions.assertEquals(first, fromBob);
    }

    @Test
    void cannotChatWithYourselfOrAnUnknownPlayer() throws Exception {
        mvc.perform(post("/api/v1/private-conversations").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipientId\":\"" + alice.getUserId() + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/private-conversations").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipientId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/private-conversations").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messagesFlowBetweenParticipantsAndTrackUnreadCounts() throws Exception {
        String conversationId = startConversation(aliceToken, bob.getUserId());
        send(aliceToken, conversationId, "  Hola Bob  ");
        send(aliceToken, conversationId, "¿Listo para la mazmorra?");

        // Bob sees the other player, the last message and two unread messages.
        mvc.perform(get("/api/v1/private-conversations").header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participantId").value(alice.getUserId().toString()))
                .andExpect(jsonPath("$[0].participantUsername").value(alice.getUsername()))
                .andExpect(jsonPath("$[0].lastMessage").value("¿Listo para la mazmorra?"))
                .andExpect(jsonPath("$[0].unreadCount").value(2));
        // The sender has nothing unread.
        mvc.perform(get("/api/v1/private-conversations/" + conversationId).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));

        mvc.perform(get("/api/v1/private-conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].content").value("Hola Bob"));

        mvc.perform(patch("/api/v1/private-conversations/" + conversationId + "/read")
                        .header("Authorization", bearer(bobToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/private-conversations/" + conversationId).header("Authorization", bearer(bobToken)))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void paginationParametersAreClamped() throws Exception {
        String conversationId = startConversation(aliceToken, bob.getUserId());
        send(aliceToken, conversationId, "uno");
        mvc.perform(get("/api/v1/private-conversations/" + conversationId + "/messages?page=-5&size=0")
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.number").value(0));
        mvc.perform(get("/api/v1/private-conversations/" + conversationId + "/messages?size=5000")
                        .header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void rejectsEmptyAndOversizedMessages() throws Exception {
        String conversationId = startConversation(aliceToken, bob.getUserId());
        mvc.perform(post("/api/v1/private-conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(aliceToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/private-conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(aliceToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outsidersCannotReadWriteOrMarkAsRead() throws Exception {
        String conversationId = startConversation(aliceToken, bob.getUserId());
        String carolToken = tokenFor(createPlayer("carol"));
        mvc.perform(get("/api/v1/private-conversations/" + conversationId).header("Authorization", bearer(carolToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/private-conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(carolToken))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/private-conversations/" + conversationId + "/messages")
                        .header("Authorization", bearer(carolToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hola\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/private-conversations/" + conversationId + "/read")
                        .header("Authorization", bearer(carolToken))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/private-conversations/" + UUID.randomUUID()).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNotFound());
    }
}
