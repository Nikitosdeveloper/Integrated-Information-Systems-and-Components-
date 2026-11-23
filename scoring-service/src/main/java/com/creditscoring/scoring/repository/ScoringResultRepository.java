package com.creditscoring.scoring.repository;

import com.creditscoring.scoring.entity.ScoringResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScoringResultRepository extends JpaRepository<ScoringResult, UUID> {
    /** Последний результат скоринга заявки (прежние результаты по другим версиям модели сохраняются как история). */
    Optional<ScoringResult> findFirstByApplicationIdOrderByCalculatedAtDesc(UUID applicationId);
}
