package com.creditscoring.application.repository;

import com.creditscoring.application.entity.CreditProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditProductRepository extends JpaRepository<CreditProduct, UUID> {
    List<CreditProduct> findByStatusOrderByName(String status);

    List<CreditProduct> findAllByOrderByName();
}
