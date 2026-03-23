package com.example.messaging.step6_idempotent_consumer;

import com.example.messaging.step6_idempotent_consumer.consumer.IdempotentPointConsumer;
import com.example.messaging.step6_idempotent_consumer.domain.PointAccount;
import com.example.messaging.step6_idempotent_consumer.repository.EventHandledRepository;
import com.example.messaging.step6_idempotent_consumer.repository.PointAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * event_handled 테이블로 중복을 방어하는 패턴을 검증한다.
 */
@SpringBootTest(classes = Step6TestConfig.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EventHandledIdempotencyTest {

    @Autowired
    IdempotentPointConsumer consumer;

    @Autowired
    PointAccountRepository pointAccountRepository;

    @Autowired
    EventHandledRepository eventHandledRepository;

    @AfterEach
    void tearDown() {
        eventHandledRepository.deleteAll();
        pointAccountRepository.deleteAll();
    }

    @Test
    void event_handled_테이블에_이미_처리된_이벤트가_있으면_스킵한다() {
        // Given: 첫 번째 처리
        boolean first = consumer.consume("evt-001", "user-1", 100);
        assertThat(first).isTrue();

        // When: 같은 이벤트 다시 처리 시도
        boolean second = consumer.consume("evt-001", "user-1", 100);

        // Then: 스킵됨
        assertThat(second).isFalse();

        PointAccount account = pointAccountRepository.findByUserId("user-1").orElseThrow();
        assertThat(account.getBalance()).isEqualTo(100L); // 중복 방어 성공
        assertThat(eventHandledRepository.existsById("evt-001")).isTrue();
    }

    @Test
    void 서로_다른_event_id의_메시지는_각각_정상_처리된다() {
        consumer.consume("evt-001", "user-1", 100);
        consumer.consume("evt-002", "user-1", 200);

        PointAccount account = pointAccountRepository.findByUserId("user-1").orElseThrow();
        assertThat(account.getBalance()).isEqualTo(300L);
        assertThat(eventHandledRepository.findAll()).hasSize(2);
    }
}
