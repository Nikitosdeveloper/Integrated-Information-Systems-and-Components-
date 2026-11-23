package com.creditscoring.analysis.entity;

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
@Table(name = "benchmark_sets")
@Getter
@NoArgsConstructor
public class BenchmarkSet {
    @Id
    private UUID id;
    private String name;
    @Setter
    private String status;
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "set", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<IndustryBenchmark> items = new ArrayList<>();
}
