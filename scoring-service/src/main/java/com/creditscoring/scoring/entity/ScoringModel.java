package com.creditscoring.scoring.entity;

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
@Table(name = "scoring_models")
@Getter
@NoArgsConstructor
public class ScoringModel {
    @Id
    private UUID id;
    private String name;
    private int version;
    @Setter
    private String status;
    private UUID createdBy;
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "model", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ModelFactor> factors = new ArrayList<>();

    @OneToMany(mappedBy = "model", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ModelRatingBand> bands = new ArrayList<>();
}
