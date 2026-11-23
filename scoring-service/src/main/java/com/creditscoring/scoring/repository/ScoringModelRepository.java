package com.creditscoring.scoring.repository;

import com.creditscoring.scoring.entity.ScoringModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoringModelRepository extends JpaRepository<ScoringModel, UUID> {
    Optional<ScoringModel> findFirstByStatus(String status);

    List<ScoringModel> findAllByOrderByVersionDesc();
}
