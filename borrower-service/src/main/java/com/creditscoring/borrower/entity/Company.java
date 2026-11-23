package com.creditscoring.borrower.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor
public class Company {
    @Id
    private UUID id;
    private String name;
    private String unp;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "industry_code")
    private Industry industry;

    @Setter
    private String address;
    @Setter
    private String phone;
}
