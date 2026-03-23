package com.example.messaging.step6_idempotent_consumer.repository;

import com.example.messaging.step6_idempotent_consumer.domain.ProductStock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {
}
