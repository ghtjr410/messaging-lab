package com.example.messaging.step4_redis_pubsub;

import com.example.messaging.step4_redis_pubsub.publisher.RedisEventPublisher;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Pub/Sub의 메시지 비보존 특성을 확인한다.
 * 구독자가 없거나 다운된 동안 발행된 메시지는 유실된다.
 */
@SpringBootTest(classes = Step4TestConfig.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Testcontainers
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RedisPubSubMessageLossTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    RedisEventPublisher publisher;

    @Autowired
    RedisMessageListenerContainer container;

    @Test
    void 구독자가_없으면_발행된_메시지는_유실된다() throws InterruptedException {
        // Given: 구독자 없이 메시지 발행
        String channel = "order-events";
        publisher.publish(channel, "이전_메시지");

        // When: 이제 구독자 등록
        AtomicReference<String> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        MessageListener listener = (msg, pattern) -> {
            received.set(new String(msg.getBody()));
            latch.countDown();
        };
        container.addMessageListener(listener, new ChannelTopic(channel));
        Thread.sleep(500);

        // Then: 이전 메시지는 수신되지 않는다
        assertThat(latch.await(2, TimeUnit.SECONDS)).isFalse();
        assertThat(received.get()).isNull();
        // → 구독자가 없었을 때 발행된 메시지는 사라졌다

        container.removeMessageListener(listener);
    }

    @Test
    void 구독자가_다운된_동안_발행된_메시지는_수신할_수_없다() throws InterruptedException {
        String channel = "order-events";
        List<String> allReceived = Collections.synchronizedList(new ArrayList<>());

        // Phase 1: 구독 중 → 수신 성공
        CountDownLatch latch1 = new CountDownLatch(1);
        MessageListener listener = (msg, pattern) -> {
            allReceived.add(new String(msg.getBody()));
            latch1.countDown();
        };
        container.addMessageListener(listener, new ChannelTopic(channel));
        Thread.sleep(500);

        publisher.publish(channel, "메시지1");
        assertThat(latch1.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(allReceived).contains("메시지1");

        // Phase 2: 구독 해제 → 메시지 유실
        container.removeMessageListener(listener);
        Thread.sleep(500);
        publisher.publish(channel, "메시지2"); // 이 메시지는 사라진다

        // Phase 3: 재구독 → 새 메시지만 수신
        CountDownLatch latch3 = new CountDownLatch(1);
        MessageListener listener2 = (msg, pattern) -> {
            allReceived.add(new String(msg.getBody()));
            latch3.countDown();
        };
        container.addMessageListener(listener2, new ChannelTopic(channel));
        Thread.sleep(500);

        publisher.publish(channel, "메시지3");
        assertThat(latch3.await(3, TimeUnit.SECONDS)).isTrue();

        // Then: 메시지1, 메시지3은 수신, 메시지2는 유실
        assertThat(allReceived).contains("메시지1", "메시지3");
        assertThat(allReceived).doesNotContain("메시지2");
        // → 다운타임 동안의 메시지는 영영 수신 불가

        container.removeMessageListener(listener2);
    }
}
