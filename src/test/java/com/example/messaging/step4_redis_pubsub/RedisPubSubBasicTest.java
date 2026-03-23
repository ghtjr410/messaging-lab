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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Pub/Sub 기본 동작을 확인한다.
 * 발행한 메시지를 구독자가 수신하는 가장 단순한 흐름.
 */
@SpringBootTest(classes = Step4TestConfig.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Testcontainers
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RedisPubSubBasicTest {

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
    void 발행한_메시지를_구독자가_수신한다() throws InterruptedException {
        // Given
        String channel = "order-events";
        String message = "{\"orderId\":1,\"event\":\"ORDER_CREATED\"}";

        AtomicReference<String> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        MessageListener listener = (msg, pattern) -> {
            received.set(new String(msg.getBody()));
            latch.countDown();
        };

        container.addMessageListener(listener, new ChannelTopic(channel));
        Thread.sleep(500); // 구독 활성화 대기

        // When
        publisher.publish(channel, message);

        // Then
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).contains("ORDER_CREATED");

        container.removeMessageListener(listener);
    }
}
