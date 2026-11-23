package com.creditscoring.application.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "analyst_conclusions")
@Getter
@Setter
@NoArgsConstructor
public class AnalystConclusion {
    @Id
    private UUID id;
    private UUID applicationId;
    private UUID analystId;
    private String text;
    private String recommendation;
    private OffsetDateTime createdAt;
}
