package com.creditscoring.analysis.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ratio_definitions")
@Getter
@NoArgsConstructor
public class RatioDefinition {
    @Id
    private String code;
    private String name;
    private String formula;
    private boolean higherIsBetter;
}
