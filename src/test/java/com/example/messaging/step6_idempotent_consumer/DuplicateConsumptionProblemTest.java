package com.example.messaging.step6_idempotent_consumer;

import com.example.messaging.step6_idempotent_consumer.consumer.NaivePointConsumer;
import com.example.messaging.step6_idempotent_consumer.domain.PointAccount;
import com.example.messaging.step6_idempotent_consumer.repository.PointAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 멱등 처리 없이 같은 메시지를 2번 소비하면 데이터가 2번 반영되는 문제를 체험한다.
 */
@SpringBootTest(classes = Step6TestConfig.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DuplicateConsumptionProblemTest {

    @Autowired
    NaivePointConsumer naivePointConsumer;

    @Autowired
    PointAccountRepository pointAccountRepository;

    @AfterEach
    void tearDown() {
        pointAccountRepository.deleteAll();
    }

    @Test
    void 같은_메시지를_2번_소비하면_포인트가_2번_적립된다() {
        // Given: 동일한 이벤트
        String eventId = "evt-001";
        String userId = "user-1";
        long points = 100;

        // When: 같은 이벤트를 2번 처리 (중복 소비 시뮬레이션)
        naivePointConsumer.consume(eventId, userId, points);
        naivePointConsumer.consume(eventId, userId, points); // 중복!

        // Then: 포인트가 200 — 기대값은 100인데 200이다. 이것이 문제다.
        PointAccount account = pointAccountRepository.findByUserId(userId).orElseThrow();
        assertThat(account.getBalance()).isEqualTo(200L);
        // → 멱등 처리 없이는 중복 적립이 발생한다
    }
}
