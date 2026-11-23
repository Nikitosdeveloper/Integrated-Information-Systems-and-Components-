package com.creditscoring.analysis.repository;

import com.creditscoring.analysis.entity.RatioDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatioDefinitionRepository extends JpaRepository<RatioDefinition, String> {
}
