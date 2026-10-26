package com.EOT.project.repository;

import com.EOT.project.model.AchievementModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AchievementRepository extends JpaRepository<AchievementModel, UUID> {

    Optional<AchievementModel> findByAchName(String achName);

    List<AchievementModel> findByIsDeletedFalse();

    List<AchievementModel> findByIsAchHiddenFalseAndIsDeletedFalse();
}

