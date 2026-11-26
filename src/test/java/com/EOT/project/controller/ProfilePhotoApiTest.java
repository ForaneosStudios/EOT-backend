package com.EOT.project.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.EOT.project.model.UserModel;
import com.EOT.project.model.UserRole;
import com.EOT.project.profilephoto.ProfilePhotoProcessorTestImages;
import com.EOT.project.profilephoto.ProfilePhotoStorage;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

class ProfilePhotoApiTest extends ApiIntegrationTestSupport {
    private static final String URL = "https://project.supabase.co/storage/v1/object/public/profile-photos/photo.jpg?v=1";
    @MockitoBean ProfilePhotoStorage storage;

    private static String body(boolean consent) {
        return "{\"image\":\"" + ProfilePhotoProcessorTestImages.jpegDataUrl(800, 600) + "\",\"consent\":" + consent + "}";
    }

    private UserModel admin() {
        return users.saveAndFlush(UserModel.builder().authUserId(UUID.randomUUID()).role(UserRole.ADMIN)
                .username("admin_" + UUID.randomUUID().toString().substring(0, 6)).email(UUID.randomUUID() + "@example.com").build());
    }

    @Test
    void aPlayerNeedsASessionToChangeTheirPhoto() throws Exception {
        mvc.perform(put("/api/v1/users/me/profile-photo").contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/users/me/profile-photo")).andExpect(status().isUnauthorized());
    }

    @Test
    void savesAProcessedPhotoAndPublishesItsUrlInTheProfile() throws Exception {
        when(storage.upload(eq(alice.getUserId()), any())).thenReturn(URL);

        mvc.perform(put("/api/v1/users/me/profile-photo").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePicture").value(URL))
                .andExpect(jsonPath("$.userId").value(alice.getUserId().toString()));

        ArgumentCaptor<byte[]> jpeg = ArgumentCaptor.forClass(byte[].class);
        verify(storage).upload(eq(alice.getUserId()), jpeg.capture());
        var image = ImageIO.read(new ByteArrayInputStream(jpeg.getValue()));
        assertThat(image.getWidth()).isEqualTo(256);
        assertThat(image.getHeight()).isEqualTo(256);
        UserModel saved = users.findById(alice.getUserId()).orElseThrow();
        assertThat(saved.getProfilePicture()).isEqualTo(URL);
        assertThat(saved.getProfilePhotoConsentAt()).isNotNull();
        // The other player is untouched.
        assertThat(users.findById(bob.getUserId()).orElseThrow().getProfilePicture()).isNull();
    }

    @Test
    void requiresTheExplicitConsent() throws Exception {
        mvc.perform(put("/api/v1/users/me/profile-photo").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body(false)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(put("/api/v1/users/me/profile-photo").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"" + ProfilePhotoProcessorTestImages.jpegDataUrl(300, 300) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(storage, never()).upload(any(), any());
    }

    @Test
    void rejectsSomethingThatIsNotAPhoto() throws Exception {
        mvc.perform(put("/api/v1/users/me/profile-photo").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"data:image/jpeg;base64,bm90IGEgcGhvdG8=\",\"consent\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        verify(storage, never()).upload(any(), any());
    }

    @Test
    void aNewerEditAlreadySavedWinsOverAnOlderOneMadeOffline() throws Exception {
        mvc.perform(put("/api/v1/users/me/profile-photo").header("Authorization", bearer(aliceToken))
                        .header("X-Client-Intent-At", "2020-01-01T00:00:00Z")
                        .contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        verify(storage, never()).upload(any(), any());
    }

    @Test
    void anUpToDateEditMadeOfflineIsApplied() throws Exception {
        when(storage.upload(eq(alice.getUserId()), any())).thenReturn(URL);
        mvc.perform(put("/api/v1/users/me/profile-photo").header("Authorization", bearer(aliceToken))
                        .header("X-Client-Intent-At", java.time.Instant.now().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profilePicture").value(URL));
    }

    @Test
    void reportsAStorageFailureSoTheAppCanRetry() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo guardar la foto. Inténtalo de nuevo."))
                .when(storage).upload(any(), any());
        mvc.perform(put("/api/v1/users/me/profile-photo").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isBadGateway());
        assertThat(users.findById(alice.getUserId()).orElseThrow().getProfilePhotoConsentAt()).isNull();
    }

    @Test
    void aPlayerRemovesTheirOwnPhoto() throws Exception {
        when(storage.upload(eq(alice.getUserId()), any())).thenReturn(URL);
        mvc.perform(put("/api/v1/users/me/profile-photo").header("Authorization", bearer(aliceToken))
                .contentType(MediaType.APPLICATION_JSON).content(body(true))).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/users/me/profile-photo").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profilePicture").doesNotExist());

        verify(storage).delete(alice.getUserId());
        assertThat(users.findById(alice.getUserId()).orElseThrow().getProfilePhotoConsentAt()).isNull();
    }

    @Test
    void removingWhenThereIsNoPhotoChangesNothingAndKeepsTheCatalogAvatar() throws Exception {
        alice.setProfilePicture("/avatars/avatar-2.svg");
        users.saveAndFlush(alice);
        mvc.perform(delete("/api/v1/users/me/profile-photo").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profilePicture").value("/avatars/avatar-2.svg"));
        verify(storage, never()).delete(any());
    }

    @Test
    void choosingACatalogAvatarReplacesThePhotoAndDeletesIt() throws Exception {
        when(storage.upload(eq(alice.getUserId()), any())).thenReturn(URL);
        mvc.perform(put("/api/v1/users/me/profile-photo").header("Authorization", bearer(aliceToken))
                .contentType(MediaType.APPLICATION_JSON).content(body(true))).andExpect(status().isOk());

        mvc.perform(patch("/api/v1/users/me/profile-picture").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"profilePicture\":\"/avatars/avatar-3.svg\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profilePicture").value("/avatars/avatar-3.svg"));

        verify(storage).delete(alice.getUserId());
        assertThat(users.findById(alice.getUserId()).orElseThrow().getProfilePhotoConsentAt()).isNull();
    }

    @Test
    void onlyAnAdministratorRemovesSomeoneElsesPhoto() throws Exception {
        when(storage.upload(eq(bob.getUserId()), any())).thenReturn(URL);
        mvc.perform(put("/api/v1/users/me/profile-photo").header("Authorization", bearer(bobToken))
                .contentType(MediaType.APPLICATION_JSON).content(body(true))).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/users/" + bob.getUserId() + "/profile-photo").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isForbidden());
        verify(storage, never()).delete(any());

        String adminToken = tokenFor(admin());
        mvc.perform(delete("/api/v1/users/" + bob.getUserId() + "/profile-photo").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profilePicture").doesNotExist());
        verify(storage).delete(bob.getUserId());
        mvc.perform(delete("/api/v1/users/" + UUID.randomUUID() + "/profile-photo").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAnAccountAlsoDeletesItsPhoto() throws Exception {
        when(storage.upload(eq(bob.getUserId()), any())).thenReturn(URL);
        mvc.perform(put("/api/v1/users/me/profile-photo").header("Authorization", bearer(bobToken))
                .contentType(MediaType.APPLICATION_JSON).content(body(true))).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/users/" + bob.getUserId()).header("Authorization", bearer(tokenFor(admin()))))
                .andExpect(status().isNoContent());

        verify(storage).delete(bob.getUserId());
    }
}
