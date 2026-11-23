package com.creditscoring.application.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "document_requests")
@Getter
@Setter
@NoArgsConstructor
public class DocumentRequest {
    @Id
    private UUID id;
    private UUID applicationId;
    private UUID requestedBy;
    private LocalDate dueDate;
    private String status;
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "request", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentRequestItem> items = new ArrayList<>();
}
