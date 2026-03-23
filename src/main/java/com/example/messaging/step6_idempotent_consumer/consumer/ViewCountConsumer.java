package com.example.messaging.step6_idempotent_consumer.consumer;

import com.example.messaging.step6_idempotent_consumer.domain.ProductViewCount;
import com.example.messaging.step6_idempotent_consumer.repository.ProductViewCountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upsert 패턴으로 집계 데이터를 멱등하게 처리하는 Consumer.
 * 같은 메시지가 와도 최종 상태를 덮어쓰므로 결과가 동일하다.
 */
@Component
public class ViewCountConsumer {

    private final ProductViewCountRepository repository;

    public ViewCountConsumer(ProductViewCountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void consume(Long productId, long count) {
        repository.findById(productId)
                .ifPresentOrElse(
                        existing -> existing.updateCount(count),
                        () -> repository.save(new ProductViewCount(productId, count))
                );
    }
}
