package com.creditscoring.statement.entity;

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

@Entity
@Table(name = "validation_issues")
@Getter
@NoArgsConstructor
public class ValidationIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id")
    private FinancialStatement statement;

    private String severity;
    private String message;

    public ValidationIssue(FinancialStatement statement, String severity, String message) {
        this.statement = statement;
        this.severity = severity;
        this.message = message;
    }
}
