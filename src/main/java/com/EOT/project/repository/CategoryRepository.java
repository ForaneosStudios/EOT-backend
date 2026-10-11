package com.EOT.project.repository;

import com.EOT.project.model.CategoryModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryModel, UUID> {

    Optional<CategoryModel> findByCategoryName(String categoryName);

    boolean existsByCategoryName(String categoryName);

    List<CategoryModel> findByIsDeletedFalse();
}

