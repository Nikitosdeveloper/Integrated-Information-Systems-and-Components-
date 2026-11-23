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

import java.math.BigDecimal;

@Entity
@Table(name = "statement_lines")
@Getter
@NoArgsConstructor
public class StatementLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id")
    private FinancialStatement statement;

    private String code;
    private String name;
    private BigDecimal value;

    public StatementLine(FinancialStatement statement, String code, String name, BigDecimal value) {
        this.statement = statement;
        this.code = code;
        this.name = name;
        this.value = value;
    }
}
