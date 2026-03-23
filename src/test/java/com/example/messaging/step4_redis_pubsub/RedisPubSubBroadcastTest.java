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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Pub/Sub의 브로드캐스트 특성을 확인한다.
 * 여러 구독자가 동일한 메시지를 모두 수신한다.
 */
@SpringBootTest(classes = Step4TestConfig.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Testcontainers
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RedisPubSubBroadcastTest {

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
    void 여러_구독자가_동일한_메시지를_모두_수신한다() throws InterruptedException {
        // Given: 3개의 독립적인 구독자
        String channel = "order-events";
        CountDownLatch latch = new CountDownLatch(3);
        List<String> receivedMessages = Collections.synchronizedList(new ArrayList<>());

        List<MessageListener> listeners = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            MessageListener listener = (msg, pattern) -> {
                receivedMessages.add(new String(msg.getBody()));
                latch.countDown();
            };
            listeners.add(listener);
            container.addMessageListener(listener, new ChannelTopic(channel));
        }
        Thread.sleep(500);

        // When: 메시지 1건 발행
        publisher.publish(channel, "ORDER_CREATED");

        // Then: 3개 구독자 모두 수신
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedMessages).hasSize(3);
        assertThat(receivedMessages).allMatch(m -> m.contains("ORDER_CREATED"));

        listeners.forEach(l -> container.removeMessageListener(l));
    }
}
