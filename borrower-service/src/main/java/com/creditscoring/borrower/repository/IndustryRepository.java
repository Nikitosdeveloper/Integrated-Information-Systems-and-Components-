package com.creditscoring.borrower.repository;

import com.creditscoring.borrower.entity.Industry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndustryRepository extends JpaRepository<Industry, String> {
}
