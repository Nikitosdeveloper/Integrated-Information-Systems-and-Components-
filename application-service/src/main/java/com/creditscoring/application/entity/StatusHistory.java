package com.creditscoring.application.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "application_status_history")
@Getter
@NoArgsConstructor
public class StatusHistory {
    @Id
    private UUID id;
    private UUID applicationId;
    private String fromStatus;
    private String toStatus;
    private UUID changedBy;
    private String comment;
    private OffsetDateTime changedAt;

    public StatusHistory(UUID applicationId, String fromStatus, String toStatus, UUID changedBy, String comment) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.comment = comment;
        this.changedAt = OffsetDateTime.now();
    }
}
