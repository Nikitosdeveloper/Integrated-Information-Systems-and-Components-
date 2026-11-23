package com.creditscoring.scoring.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "scoring_results")
@Getter
@Setter
@NoArgsConstructor
public class ScoringResult {
    @Id
    private UUID id;
    private UUID applicationId;
    private UUID modelId;
    private BigDecimal totalScore;
    private String rating;
    private BigDecimal recommendedLimit;
    private boolean stopTriggered;
    private OffsetDateTime calculatedAt;

    @OneToMany(mappedBy = "result", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<FactorScore> factorScores = new ArrayList<>();
}
