package com.creditscoring.borrower.repository;

import com.creditscoring.borrower.entity.Company;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    @Override
    @EntityGraph(attributePaths = "industry")
    Optional<Company> findById(UUID id);

    @EntityGraph(attributePaths = "industry")
    List<Company> findByIdIn(Collection<UUID> ids);
}
