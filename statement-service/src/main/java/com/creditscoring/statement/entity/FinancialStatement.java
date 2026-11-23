package com.creditscoring.statement.entity;

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
@Table(name = "financial_statements")
@Getter
@Setter
@NoArgsConstructor
public class FinancialStatement {
    @Id
    private UUID id;
    private UUID companyId;
    private String formType;
    private int periodYear;
    private String status;
    private UUID createdBy;
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "statement", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StatementLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "statement", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ValidationIssue> issues = new ArrayList<>();
}
