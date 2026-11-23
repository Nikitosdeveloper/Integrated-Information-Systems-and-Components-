package com.creditscoring.analysis.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "financial_analyses")
@Getter
@Setter
@NoArgsConstructor
public class FinancialAnalysis {
    @Id
    private UUID id;
    private UUID applicationId;
    private UUID companyId;
    private UUID benchmarkSetId;
    private OffsetDateTime calculatedAt;

    @OneToMany(mappedBy = "analysis", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RatioValue> values = new ArrayList<>();
}
