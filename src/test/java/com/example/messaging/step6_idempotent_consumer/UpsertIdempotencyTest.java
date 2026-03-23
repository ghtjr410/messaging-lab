package com.example.messaging.step6_idempotent_consumer;

import com.example.messaging.step6_idempotent_consumer.consumer.ViewCountConsumer;
import com.example.messaging.step6_idempotent_consumer.domain.ProductViewCount;
import com.example.messaging.step6_idempotent_consumer.repository.ProductViewCountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upsert 패턴으로 집계 데이터를 멱등하게 처리하는 것을 검증한다.
 */
@SpringBootTest(classes = Step6TestConfig.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UpsertIdempotencyTest {

    @Autowired
    ViewCountConsumer consumer;

    @Autowired
    ProductViewCountRepository repository;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void 같은_이벤트를_2번_처리해도_upsert로_올바른_결과가_유지된다() {
        // Given: "상품 1001 조회수 = 150"
        consumer.consume(1001L, 150);

        // When: 같은 이벤트 다시 처리 (중복)
        consumer.consume(1001L, 150);

        // Then: 150 (중복 적용 안 됨)
        ProductViewCount viewCount = repository.findById(1001L).orElseThrow();
        assertThat(viewCount.getCount()).isEqualTo(150L);
    }

    @Test
    void upsert는_최신_값으로_덮어쓰므로_최종_상태가_보장된다() {
        consumer.consume(1001L, 100);
        consumer.consume(1001L, 150); // 업데이트

        ProductViewCount viewCount = repository.findById(1001L).orElseThrow();
        assertThat(viewCount.getCount()).isEqualTo(150L);
    }

    @Test
    void 다른_상품의_이벤트는_각각_독립적으로_upsert된다() {
        consumer.consume(1001L, 150);
        consumer.consume(1002L, 80);

        assertThat(repository.findById(1001L).orElseThrow().getCount()).isEqualTo(150L);
        assertThat(repository.findById(1002L).orElseThrow().getCount()).isEqualTo(80L);
    }
}
