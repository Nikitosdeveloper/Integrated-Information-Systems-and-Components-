package com.creditscoring.application.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_applications")
@Getter
@Setter
@NoArgsConstructor
public class CreditApplication {
    @Id
    private UUID id;
    private UUID companyId;
    private String companyName;
    private String industryCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private CreditProduct product;

    private BigDecimal amount;
    private int termMonths;
    private String purpose;
    private String collateral;
    private String status;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime submittedAt;
    private UUID assignedAnalystId;
    private String latestRating;
    private BigDecimal latestScore;
    private BigDecimal recommendedLimit;
}
