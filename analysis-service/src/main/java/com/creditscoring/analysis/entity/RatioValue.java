package com.creditscoring.analysis.entity;

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
@Table(name = "ratio_values")
@Getter
@NoArgsConstructor
public class RatioValue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id")
    private FinancialAnalysis analysis;

    private String ratioCode;
    private int periodYear;
    private BigDecimal value;
    private String assessment;

    public RatioValue(FinancialAnalysis analysis, String ratioCode, int periodYear, BigDecimal value, String assessment) {
        this.analysis = analysis;
        this.ratioCode = ratioCode;
        this.periodYear = periodYear;
        this.value = value;
        this.assessment = assessment;
    }
}
