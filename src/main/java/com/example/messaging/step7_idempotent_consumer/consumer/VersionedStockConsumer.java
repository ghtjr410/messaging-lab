package com.example.messaging.step7_idempotent_consumer.consumer;

import com.example.messaging.step7_idempotent_consumer.domain.ProductStock;
import com.example.messaging.step7_idempotent_consumer.repository.ProductStockRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * version 비교로 순서 역전까지 방어하는 Consumer.
 * 현재 version보다 높은 이벤트만 반영한다.
 *
 * UPDATE WHERE version < :newVersion으로 원자적으로 처리하여
 * 동시에 같은 productId에 대한 이벤트가 들어와도 안전하다.
 */
@Component
public class VersionedStockConsumer {

    private final ProductStockRepository repository;

    public VersionedStockConsumer(ProductStockRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean consume(Long productId, long newStock, int newVersion) {
        // 기존 레코드가 없으면 새로 생성
        if (!repository.existsById(productId)) {
            repository.save(new ProductStock(productId, newStock, newVersion));
            return true;
        }

        // UPDATE WHERE version < newVersion — 원자적 비교+갱신
        int updated = repository.updateIfNewer(productId, newStock, newVersion);
        return updated > 0;
    }
}
