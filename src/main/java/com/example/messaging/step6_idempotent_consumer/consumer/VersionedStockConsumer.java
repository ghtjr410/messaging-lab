package com.example.messaging.step6_idempotent_consumer.consumer;

import com.example.messaging.step6_idempotent_consumer.domain.ProductStock;
import com.example.messaging.step6_idempotent_consumer.repository.ProductStockRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * version 비교로 순서 역전까지 방어하는 Consumer.
 * 현재 version보다 높은 이벤트만 반영한다.
 */
@Component
public class VersionedStockConsumer {

    private final ProductStockRepository repository;

    public VersionedStockConsumer(ProductStockRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean consume(Long productId, long newStock, int newVersion) {
        ProductStock stock = repository.findById(productId).orElse(null);
        if (stock == null) {
            repository.save(new ProductStock(productId, newStock, newVersion));
            return true;
        }
        return stock.applyIfNewer(newStock, newVersion);
    }
}
