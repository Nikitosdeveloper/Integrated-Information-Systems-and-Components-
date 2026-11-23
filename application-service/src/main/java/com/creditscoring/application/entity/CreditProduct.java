package com.creditscoring.application.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "credit_products")
@Getter
@NoArgsConstructor
public class CreditProduct {
    @Id
    private UUID id;
    private String name;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private int maxTermMonths;
    private BigDecimal interestRate;
    private String status;
}
