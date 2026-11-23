package com.creditscoring.auth.repository;

import com.creditscoring.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

    @EntityGraph(attributePaths = "role")
    Optional<UserEntity> findByEmailIgnoreCase(String email);

    @Override
    @EntityGraph(attributePaths = "role")
    Optional<UserEntity> findById(UUID id);

    @EntityGraph(attributePaths = "role")
    List<UserEntity> findByIdIn(Collection<UUID> ids);

    @Override
    @EntityGraph(attributePaths = "role")
    Page<UserEntity> findAll(Specification<UserEntity> spec, Pageable pageable);

    boolean existsByEmailIgnoreCase(String email);
}
