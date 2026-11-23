package com.creditscoring.application.repository;

import com.creditscoring.application.entity.CreditApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditApplicationRepository extends JpaRepository<CreditApplication, UUID>, JpaSpecificationExecutor<CreditApplication> {

    @Override
    @EntityGraph(attributePaths = "product")
    Optional<CreditApplication> findById(UUID id);

    /** Продукт загружается тем же запросом (join): без N+1 при выводе страницы заявок. */
    @Override
    @EntityGraph(attributePaths = "product")
    Page<CreditApplication> findAll(Specification<CreditApplication> spec, Pageable pageable);

    long countByCompanyIdAndStatusIn(UUID companyId, Collection<String> statuses);

    List<CreditApplication> findByLatestRatingIsNullAndStatusIn(Collection<String> statuses);
}
