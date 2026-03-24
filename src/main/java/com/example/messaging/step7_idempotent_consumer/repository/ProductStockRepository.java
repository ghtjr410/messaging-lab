package com.example.messaging.step7_idempotent_consumer.repository;

import com.example.messaging.step7_idempotent_consumer.domain.ProductStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    @Modifying
    @Query("UPDATE ProductStock s SET s.stock = :newStock, s.version = :newVersion " +
           "WHERE s.productId = :productId AND s.version < :newVersion")
    int updateIfNewer(Long productId, long newStock, int newVersion);
}
