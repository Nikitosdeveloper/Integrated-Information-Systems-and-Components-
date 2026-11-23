package com.creditscoring.auth.repository;

import com.creditscoring.auth.entity.RoleEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, String> {

    @Override
    @EntityGraph(attributePaths = "permissions")
    List<RoleEntity> findAll();

    @Override
    @EntityGraph(attributePaths = "permissions")
    Optional<RoleEntity> findById(String code);
}
