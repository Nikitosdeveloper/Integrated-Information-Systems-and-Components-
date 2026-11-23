package com.creditscoring.statement.repository;

import com.creditscoring.statement.entity.FinancialStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FinancialStatementRepository extends JpaRepository<FinancialStatement, UUID> {

    List<FinancialStatement> findByCompanyIdOrderByPeriodYearDescFormTypeAsc(UUID companyId);

    List<FinancialStatement> findByCompanyIdAndStatusOrderByPeriodYearDescFormTypeAsc(UUID companyId, String status);

    List<FinancialStatement> findByIdIn(Collection<UUID> ids);

    boolean existsByCompanyIdAndFormTypeAndPeriodYear(UUID companyId, String formType, int periodYear);
}
