package com.creditscoring.analysis.repository;

import com.creditscoring.analysis.entity.BenchmarkSet;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BenchmarkSetRepository extends JpaRepository<BenchmarkSet, UUID> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<BenchmarkSet> findById(UUID id);

    @EntityGraph(attributePaths = "items")
    Optional<BenchmarkSet> findFirstByStatus(String status);

    List<BenchmarkSet> findAllByOrderByCreatedAtDesc();
}
