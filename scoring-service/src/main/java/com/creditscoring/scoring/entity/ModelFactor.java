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
@Table(name = "model_factors")
@Getter
@NoArgsConstructor
public class ModelFactor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id")
    private ScoringModel model;

    private String ratioCode;
    private BigDecimal weight;
    private boolean isStopFactor;
    private BigDecimal badValue;
    private BigDecimal goodValue;
    private BigDecimal stopValue;
}
