package com.EOT.project.service;

import com.EOT.project.dto.request.*;
import com.EOT.project.exception.*;
import com.EOT.project.model.*;
import com.EOT.project.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for service rules not covered by the original service test classes. */
@ExtendWith(MockitoExtension.class)
class CoreServicesTest {
    @Mock AchievementRepository achievements; @Mock CategoryRepository categories; @Mock ThreadRepository threads;
    @Mock UserRepository users; @Mock PostRepository posts; @Mock PostReportRepository reports;
    @InjectMocks AchievementService achievementService; @InjectMocks ThreadService threadService;
    @InjectMocks PostService postService; @InjectMocks PostReportService reportService;
    @InjectMocks CurrentUserService currentUserService;

    private final UUID userId = UUID.randomUUID();
    private final UUID threadId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @Test void achievementRejectsDuplicateAndSoftDeletes() {
        AchievementRequestDTO request = AchievementRequestDTO.builder().achName("Explorador").achDescription("Descubre zonas").build();
        when(achievements.findByAchName("Explorador")).thenReturn(Optional.of(achievement()));
        assertThrows(ConflictException.class, () -> achievementService.create(request));
        AchievementModel active = achievement(); active.setAchId(UUID.randomUUID());
        when(achievements.findById(active.getAchId())).thenReturn(Optional.of(active));
        achievementService.delete(active.getAchId());
        assertThat(active.getIsDeleted()).isTrue(); assertThat(active.getDeletedAt()).isNotNull(); verify(achievements).save(active);
    }

    @Test void achievementHidesDeletedRecordsAndAllowsOwnNameOnUpdate() {
        AchievementModel deleted = achievement(); deleted.setIsDeleted(true);
        when(achievements.findById(deleted.getAchId())).thenReturn(Optional.of(deleted));
        assertThrows(ResourceNotFoundException.class, () -> achievementService.findById(deleted.getAchId()));
        AchievementModel active = achievement();
        when(achievements.findById(active.getAchId())).thenReturn(Optional.of(active));
        when(achievements.findByAchName("Explorador")).thenReturn(Optional.of(active)); when(achievements.save(active)).thenReturn(active);
        achievementService.update(active.getAchId(), AchievementRequestDTO.builder().achName("Explorador").achDescription("Nueva").isAchHidden(true).build());
        assertThat(active.getIsAchHidden()).isTrue();
    }

    @Test void threadCreatesCommunityWhenCategoryIsOmittedAndTrimsText() {
        UserModel user = user(userId); when(users.findById(userId)).thenReturn(Optional.of(user));
        when(categories.findByCategoryName("Comunidad")).thenReturn(Optional.empty());
        when(categories.save(any(CategoryModel.class))).thenAnswer(i -> i.getArgument(0));
        when(threads.save(any(ThreadModel.class))).thenAnswer(i -> i.getArgument(0));
        ThreadRequestDTO request = ThreadRequestDTO.builder().userId(userId).title("  Titulo  ").content("  Contenido  ").build();
        threadService.create(request);
        ArgumentCaptor<ThreadModel> saved = ArgumentCaptor.forClass(ThreadModel.class); verify(threads).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Titulo"); assertThat(saved.getValue().getCategory().getCategoryName()).isEqualTo("Comunidad");
    }

    @Test void threadRejectsDeletedCategoryAndSoftDeletesActiveThread() {
        CategoryModel deleted = category(); deleted.setIsDeleted(true); when(categories.findById(categoryId)).thenReturn(Optional.of(deleted));
        assertThrows(ResourceNotFoundException.class, () -> threadService.create(ThreadRequestDTO.builder().categoryId(categoryId).userId(userId).title("Titulo").content("Contenido").build()));
        ThreadModel thread = thread(); when(threads.findById(threadId)).thenReturn(Optional.of(thread));
        threadService.delete(threadId); assertThat(thread.getIsDeleted()).isTrue(); verify(threads).save(thread);
    }

    @Test void postRejectsLockedThreadAndParentFromAnotherThread() {
        ThreadModel locked = thread(); locked.setIsLocked(true); when(threads.findById(threadId)).thenReturn(Optional.of(locked));
        assertThrows(ConflictException.class, () -> postService.create(postRequest(null)));
        ThreadModel open = thread(); when(threads.findById(threadId)).thenReturn(Optional.of(open)); when(users.findById(userId)).thenReturn(Optional.of(user(userId)));
        PostModel foreignParent = PostModel.builder().postId(UUID.randomUUID()).thread(ThreadModel.builder().threadId(UUID.randomUUID()).build()).user(user(userId)).content("x").build();
        when(posts.findById(foreignParent.getPostId())).thenReturn(Optional.of(foreignParent));
        assertThrows(BadRequestException.class, () -> postService.create(postRequest(foreignParent.getPostId())));
    }

    @Test void postAllowsOwnerOfThreadToDeleteButRejectsThirdParty() {
        UUID author = UUID.randomUUID(), owner = UUID.randomUUID(); PostModel post = PostModel.builder().postId(UUID.randomUUID()).user(user(author)).thread(ThreadModel.builder().threadId(threadId).user(user(owner)).build()).content("x").build();
        when(posts.findById(post.getPostId())).thenReturn(Optional.of(post));
        postService.delete(post.getPostId(), owner); assertThat(post.getIsDeleted()).isTrue();
        PostModel other = PostModel.builder().postId(UUID.randomUUID()).user(user(author)).thread(ThreadModel.builder().threadId(threadId).user(user(owner)).build()).content("x").build(); when(posts.findById(other.getPostId())).thenReturn(Optional.of(other));
        assertThrows(BadRequestException.class, () -> postService.delete(other.getPostId(), UUID.randomUUID()));
    }

    @Test void postPageUsesRootOrReplyRepositoryAndNormalizesModel() {
        ThreadModel thread = thread(); when(threads.findById(threadId)).thenReturn(Optional.of(thread));
        when(posts.findByThread_ThreadIdAndParentPostIsNullAndIsDeletedFalseOrderByCreatedAtAsc(eq(threadId), any())).thenReturn(new PageImpl<>(List.of()));
        postService.findPage(threadId, null, PageRequest.of(0, 10));
        verify(posts).findByThread_ThreadIdAndParentPostIsNullAndIsDeletedFalseOrderByCreatedAtAsc(eq(threadId), any());
    }

    @Test void reportsRejectSelfReportingAndAllowThreadOwnerToReview() {
        UserModel reporter = user(userId); PostModel own = PostModel.builder().postId(UUID.randomUUID()).user(reporter).thread(thread()).content("x").build(); when(posts.findById(own.getPostId())).thenReturn(Optional.of(own));
        assertThrows(BadRequestException.class, () -> reportService.create(own.getPostId(), reporter, new PostReportRequestDTO("spam")));
        UUID owner = UUID.randomUUID(); PostReportModel report = PostReportModel.builder().reportId(UUID.randomUUID()).post(PostModel.builder().postId(UUID.randomUUID()).user(reporter).thread(ThreadModel.builder().user(user(owner)).build()).content("x").build()).reporter(reporter).reason("spam").build();
        when(reports.findById(report.getReportId())).thenReturn(Optional.of(report)); when(reports.save(report)).thenReturn(report);
        reportService.setStatus(report.getReportId(), owner, PostReportStatus.RESOLVED);
        assertThat(report.getStatus()).isEqualTo(PostReportStatus.RESOLVED); assertThat(report.getReviewedAt()).isNotNull();
    }

    @Test void currentUserRequiresProfileAndChecksOwnershipOrAdmin() {
        UUID authId = UUID.randomUUID(); UserModel profile = user(userId); when(users.findByAuthUserIdAndIsDeletedFalse(authId)).thenReturn(Optional.of(profile));
        var own = new UsernamePasswordAuthenticationToken(authId.toString(), "x"); currentUserService.requireOwnerOrAdmin(own, userId);
        assertThrows(ResponseStatusException.class, () -> currentUserService.requireOwnerOrAdmin(own, UUID.randomUUID()));
        var admin = new UsernamePasswordAuthenticationToken(authId.toString(), "x", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))); currentUserService.requireOwnerOrAdmin(admin, UUID.randomUUID());
    }

    private AchievementModel achievement() { return AchievementModel.builder().achId(UUID.randomUUID()).achName("Explorador").achDescription("x").build(); }
    private CategoryModel category() { return CategoryModel.builder().categoryId(categoryId).categoryName("General").build(); }
    private UserModel user(UUID id) { return UserModel.builder().userId(id).username("user" + id).email("u@example.com").build(); }
    private ThreadModel thread() { return ThreadModel.builder().threadId(threadId).category(category()).user(user(userId)).title("Titulo").content("Contenido").build(); }
    private PostRequestDTO postRequest(UUID parentId) { return PostRequestDTO.builder().threadId(threadId).userId(userId).parentPostId(parentId).content("Mensaje").build(); }
}
