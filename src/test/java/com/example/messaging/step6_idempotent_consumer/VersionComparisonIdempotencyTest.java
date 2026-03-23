package com.example.messaging.step6_idempotent_consumer;

import com.example.messaging.step6_idempotent_consumer.consumer.VersionedStockConsumer;
import com.example.messaging.step6_idempotent_consumer.domain.ProductStock;
import com.example.messaging.step6_idempotent_consumer.repository.ProductStockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * version 비교로 순서 역전까지 방어하는 패턴을 검증한다.
 */
@SpringBootTest(classes = Step6TestConfig.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class VersionComparisonIdempotencyTest {

    @Autowired
    VersionedStockConsumer consumer;

    @Autowired
    ProductStockRepository repository;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void version이_현재보다_높은_이벤트만_반영된다() {
        // Given: 초기 상태
        consumer.consume(1001L, 100, 1);

        // When: v2 이벤트 도착
        boolean applied = consumer.consume(1001L, 80, 2);

        // Then
        assertThat(applied).isTrue();
        ProductStock stock = repository.findById(1001L).orElseThrow();
        assertThat(stock.getStock()).isEqualTo(80L);
        assertThat(stock.getVersion()).isEqualTo(2);
    }

    @Test
    void version이_현재보다_낮거나_같은_이벤트는_무시된다() {
        // Given: version 3 상태
        consumer.consume(1001L, 100, 1);
        consumer.consume(1001L, 80, 3);

        // When: v2 이벤트 지연 도착
        boolean applied = consumer.consume(1001L, 90, 2);

        // Then: 무시됨
        assertThat(applied).isFalse();
        ProductStock stock = repository.findById(1001L).orElseThrow();
        assertThat(stock.getStock()).isEqualTo(80L);  // v3 값 유지
        assertThat(stock.getVersion()).isEqualTo(3);
    }

    @Test
    void 순서가_역전된_이벤트_시퀀스에서_최종_상태가_올바르다() {
        // v1: 초기
        consumer.consume(1001L, 100, 1);

        // v3 먼저 도착 (순서 역전)
        consumer.consume(1001L, 50, 3);

        // v2 지연 도착 → 무시
        consumer.consume(1001L, 80, 2);

        // v4 도착
        consumer.consume(1001L, 30, 4);

        ProductStock stock = repository.findById(1001L).orElseThrow();
        assertThat(stock.getStock()).isEqualTo(30L);
        assertThat(stock.getVersion()).isEqualTo(4);
    }
}
