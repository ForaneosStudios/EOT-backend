package com.EOT.project.controller;

import com.EOT.project.dto.request.*;
import com.EOT.project.dto.response.*;
import com.EOT.project.model.*;
import com.EOT.project.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Controller unit tests: response status, delegation and ownership checks. */
@ExtendWith(MockitoExtension.class)
class RemainingControllersTest {
    @Mock CategoryService categories; @Mock PostService posts; @Mock PostReportService reports;
    @Mock UserService users; @Mock UserAchievementService userAchievements; @Mock CurrentUserService currentUser;
    @InjectMocks CategoryController categoryController; @InjectMocks PostController postController;
    @InjectMocks PostReportController reportController; @InjectMocks UserController userController;
    @InjectMocks UserAchievementController achievementController;

    @Test void categoryCrudReturnsExpectedStatusesAndDelegates() {
        UUID id = UUID.randomUUID(); CategoryRequestDTO request = new CategoryRequestDTO("General", "x"); CategoryResponseDTO created = CategoryResponseDTO.builder().categoryId(id).build();
        when(categories.create(request)).thenReturn(created); when(categories.update(id, request)).thenReturn(created);
        assertThat(categoryController.createCategory(request).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(categoryController.updateCategory(id, request).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(categoryController.deleteCategory(id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(categories).delete(id);
    }

    @Test void postCreationAndUpdateRequireResourceOwner() {
        UUID author = UUID.randomUUID(), postId = UUID.randomUUID(); Authentication auth = new UsernamePasswordAuthenticationToken("auth", "x");
        PostRequestDTO request = PostRequestDTO.builder().threadId(UUID.randomUUID()).userId(author).content("Mensaje").build();
        PostResponseDTO existing = PostResponseDTO.builder().postId(postId).userId(author).build();
        when(posts.create(request)).thenReturn(existing); when(posts.findById(postId)).thenReturn(existing); when(posts.update(eq(postId), any(), eq(false))).thenReturn(existing);
        assertThat(postController.createPost(request, auth).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postController.updatePost(postId, new PostUpdateRequestDTO("editado"), auth).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(currentUser, times(2)).requireOwnerOrAdmin(auth, author);
    }

    @Test void postDeletionUsesAuthenticatedProfileRatherThanRequestData() {
        UUID postId = UUID.randomUUID(), profileId = UUID.randomUUID(); Authentication auth = new UsernamePasswordAuthenticationToken("auth", "x");
        when(posts.findById(postId)).thenReturn(PostResponseDTO.builder().postId(postId).userId(profileId).build());
        when(currentUser.requireProfile(auth)).thenReturn(UserModel.builder().userId(profileId).build());
        assertThat(postController.deletePost(postId, auth).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(posts).delete(postId, profileId);
    }

    @Test void reportEndpointsUseProfileAndThreadAuthorization() {
        UUID postId = UUID.randomUUID(), threadId = UUID.randomUUID(), reportId = UUID.randomUUID(), profileId = UUID.randomUUID(); Authentication auth = new UsernamePasswordAuthenticationToken("auth", "x");
        UserModel profile = UserModel.builder().userId(profileId).build(); when(currentUser.requireProfile(auth)).thenReturn(profile);
        PostReportResponseDTO response = PostReportResponseDTO.builder().reportId(reportId).build(); when(reports.create(eq(postId), same(profile), any())).thenReturn(response); when(reports.setStatus(reportId, profileId, PostReportStatus.DISMISSED)).thenReturn(response);
        assertThat(reportController.report(postId, new PostReportRequestDTO("spam"), auth)).isSameAs(response);
        assertThat(reportController.review(reportId, new PostReportStatusRequestDTO(PostReportStatus.DISMISSED), auth)).isSameAs(response);
        reportController.list(threadId, auth, -1); verify(reports).findForThread(eq(threadId), eq(profileId), argThat(page -> page.getPageNumber() == 0));
    }

    @Test void userAchievementEndpointsScopeCurrentAndSpecifiedUser() {
        UUID userId = UUID.randomUUID(), achId = UUID.randomUUID(); Authentication auth = new UsernamePasswordAuthenticationToken("auth", "x");
        when(currentUser.requireProfile(auth)).thenReturn(UserModel.builder().userId(userId).build());
        assertThat(achievementController.getCurrentUserAchievements(auth).getStatusCode()).isEqualTo(HttpStatus.OK);
        UserAchievementProgressDTO request = UserAchievementProgressDTO.builder().userId(userId).achId(achId).progress(1).build();
        achievementController.updateAchievementProgress(request, auth);
        verify(currentUser).requireOwnerOrAdmin(auth, userId); verify(userAchievements).updateProgress(request);
    }

    @Test void userEndpointsUseCurrentProfileForAvatarAndAvailabilityIsPublic() {
        UUID userId = UUID.randomUUID(); Authentication auth = new UsernamePasswordAuthenticationToken("auth", "x");
        when(users.isUsernameAvailable("new_user")).thenReturn(true); when(currentUser.requireProfile(auth)).thenReturn(UserModel.builder().userId(userId).build());
        assertThat(userController.usernameAvailability("new_user").available()).isTrue();
        userController.updateProfilePicture(auth, new UpdateProfilePictureRequest("/avatars/avatar-1.svg"));
        verify(users).updateProfilePicture(userId, new UpdateProfilePictureRequest("/avatars/avatar-1.svg"));
    }
}
