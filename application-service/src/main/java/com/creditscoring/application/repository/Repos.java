package com.creditscoring.application.repository;

import com.creditscoring.application.entity.AnalystConclusion;
import com.creditscoring.application.entity.CreditDecision;
import com.creditscoring.application.entity.DocumentRequest;
import com.creditscoring.application.entity.DocumentRequestItem;
import com.creditscoring.application.entity.StatusHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Небольшие репозитории агрегата заявки. */
public final class Repos {

    public interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {
        List<StatusHistory> findByApplicationIdOrderByChangedAtAsc(UUID applicationId);
    }

    public interface DocumentRequestRepository extends JpaRepository<DocumentRequest, UUID> {
        @EntityGraph(attributePaths = "items")
        List<DocumentRequest> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

        @EntityGraph(attributePaths = "items")
        Optional<DocumentRequest> findByApplicationIdAndStatus(UUID applicationId, String status);
    }

    public interface DocumentRequestItemRepository extends JpaRepository<DocumentRequestItem, UUID> {
    }

    public interface AnalystConclusionRepository extends JpaRepository<AnalystConclusion, UUID> {
        Optional<AnalystConclusion> findByApplicationId(UUID applicationId);
    }

    public interface CreditDecisionRepository extends JpaRepository<CreditDecision, UUID> {
        Optional<CreditDecision> findByApplicationId(UUID applicationId);
    }

    private Repos() {
    }
}
