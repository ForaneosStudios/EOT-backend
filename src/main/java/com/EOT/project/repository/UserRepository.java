package com.EOT.project.repository;

import com.EOT.project.model.UserModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserModel, UUID> {

    Optional<UserModel> findByUsername(String username);

    Optional<UserModel> findByEmail(String email);

    Optional<UserModel> findByEmailAndIsDeletedFalse(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<UserModel> findByIsDeletedFalse();
}

