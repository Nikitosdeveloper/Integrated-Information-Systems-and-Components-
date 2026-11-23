package com.creditscoring.analysis.repository;

import com.creditscoring.analysis.entity.FinancialAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FinancialAnalysisRepository extends JpaRepository<FinancialAnalysis, UUID> {
    Optional<FinancialAnalysis> findByApplicationId(UUID applicationId);
}
