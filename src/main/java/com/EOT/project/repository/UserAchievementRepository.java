package com.EOT.project.repository;

import com.EOT.project.model.UserAchievementModel;
import com.EOT.project.model.UserAchievementModelId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievementModel, UserAchievementModelId> {

    List<UserAchievementModel> findById_UserId(UUID userId);

    List<UserAchievementModel> findById_UserIdAndIsUnlockedTrue(UUID userId);
}

