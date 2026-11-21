package com.EOT.project.repository;

import com.EOT.project.model.UserModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserModel, UUID> {

    Optional<UserModel> findByUsername(String username);

    Optional<UserModel> findByEmail(String email);

    Optional<UserModel> findByEmailAndIsDeletedFalse(String email);

    Optional<UserModel> findByAuthUserIdAndIsDeletedFalse(UUID authUserId);

    boolean existsByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmail(String email);

    List<UserModel> findByIsDeletedFalse();

    /** Loads the row with a write lock so concurrent edits are applied one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from UserModel e where e.userId = :id")
    Optional<UserModel> findByIdForUpdate(@Param("id") UUID id);
}
