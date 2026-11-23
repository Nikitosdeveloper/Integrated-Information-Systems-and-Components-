package com.creditscoring.scoring.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "factor_scores")
@Getter
@NoArgsConstructor
public class FactorScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id")
    private ScoringResult result;

    private String ratioCode;
    private BigDecimal ratioValue;
    private BigDecimal score;
    private BigDecimal weight;

    public FactorScore(ScoringResult result, String ratioCode, BigDecimal ratioValue, BigDecimal score, BigDecimal weight) {
        this.result = result;
        this.ratioCode = ratioCode;
        this.ratioValue = ratioValue;
        this.score = score;
        this.weight = weight;
    }
}
