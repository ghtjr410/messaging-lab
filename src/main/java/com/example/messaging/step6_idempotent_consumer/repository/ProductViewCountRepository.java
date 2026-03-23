package com.example.messaging.step6_idempotent_consumer.repository;

import com.example.messaging.step6_idempotent_consumer.domain.ProductViewCount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductViewCountRepository extends JpaRepository<ProductViewCount, Long> {
}
