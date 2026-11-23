package com.creditscoring.application.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_decisions")
@Getter
@Setter
@NoArgsConstructor
public class CreditDecision {
    @Id
    private UUID id;
    private UUID applicationId;
    private UUID decidedBy;
    private String decision;
    private BigDecimal approvedAmount;
    private String comment;
    private OffsetDateTime decidedAt;
}
