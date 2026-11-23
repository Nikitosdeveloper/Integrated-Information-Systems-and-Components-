package com.creditscoring.borrower.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "industries")
@Getter
@NoArgsConstructor
public class Industry {
    @Id
    private String code;
    private String name;
}
