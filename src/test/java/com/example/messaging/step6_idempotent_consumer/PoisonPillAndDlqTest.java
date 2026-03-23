package com.example.messaging.step6_idempotent_consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import com.example.messaging.step5_kafka.KafkaTestBase;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Poison pill이 Consumer를 막는 문제와, DLQ 격리 개념을 체험한다.
 * Spring Kafka의 ErrorHandler 대신 순수 Kafka API로 개념을 증명한다.
 */
class PoisonPillAndDlqTest extends KafkaTestBase {

    @Test
    void 파싱_불가능한_메시지가_Consumer를_막는다() throws Exception {
        String topic = "poison-pill-test";
        createTopic(topic, 1);

        try (KafkaProducer<String, String> producer = createProducer()) {
            producer.send(new ProducerRecord<>(topic, "key-1", "{\"orderId\":1,\"points\":100}")).get();
            producer.send(new ProducerRecord<>(topic, "key-2", "{{{{invalid json")).get();  // poison pill
            producer.send(new ProducerRecord<>(topic, "key-3", "{\"orderId\":3,\"points\":300}")).get();
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        try (KafkaConsumer<String, String> consumer = createConsumer("poison-group")) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> records = pollMessages(consumer, 3, Duration.ofSeconds(10));

            for (ConsumerRecord<String, String> record : records) {
                try {
                    // JSON 파싱 시도
                    if (record.value().contains("{{{{")) {
                        throw new RuntimeException("파싱 실패: " + record.value());
                    }
                    successCount.incrementAndGet();
                } catch (RuntimeException e) {
                    failureCount.incrementAndGet();
                    // 실제 환경에서는 여기서 무한 재시도에 빠질 수 있다
                }
            }
        }

        // poison pill 1건 실패, 정상 2건 성공
        assertThat(failureCount.get()).isEqualTo(1);
        assertThat(successCount.get()).isEqualTo(2);
        // 실제로는 offset 커밋 전략에 따라 poison pill에서 영원히 멈출 수 있다
    }

    @Test
    void 처리_실패한_메시지를_DLQ_토픽으로_격리할_수_있다() throws Exception {
        String topic = "dlq-source-test";
        String dlqTopic = topic + ".DLT";
        createTopic(topic, 1);
        createTopic(dlqTopic, 1);

        try (KafkaProducer<String, String> producer = createProducer()) {
            producer.send(new ProducerRecord<>(topic, "key-1", "{\"orderId\":1,\"points\":100}")).get();
            producer.send(new ProducerRecord<>(topic, "key-2", "{{{{invalid json")).get();
            producer.send(new ProducerRecord<>(topic, "key-3", "{\"orderId\":3,\"points\":300}")).get();
        }

        AtomicInteger successCount = new AtomicInteger(0);

        // Consumer: 실패 시 DLQ로 격리
        try (KafkaProducer<String, String> dlqProducer = createProducer();
             KafkaConsumer<String, String> consumer = createConsumer("dlq-group")) {

            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> records = pollMessages(consumer, 3, Duration.ofSeconds(10));

            for (ConsumerRecord<String, String> record : records) {
                try {
                    if (record.value().contains("{{{{")) {
                        throw new RuntimeException("파싱 실패");
                    }
                    successCount.incrementAndGet();
                } catch (RuntimeException e) {
                    // DLQ로 격리
                    dlqProducer.send(new ProducerRecord<>(dlqTopic, record.key(), record.value())).get();
                }
            }
            consumer.commitSync();
        }

        // 정상 메시지는 처리됨
        assertThat(successCount.get()).isEqualTo(2);

        // DLQ에서 격리된 메시지 확인
        try (KafkaConsumer<String, String> dlqConsumer = createConsumer("dlq-verify-group")) {
            dlqConsumer.subscribe(List.of(dlqTopic));
            List<ConsumerRecord<String, String>> dlqRecords = pollMessages(dlqConsumer, 1, Duration.ofSeconds(10));

            assertThat(dlqRecords).hasSize(1);
            assertThat(dlqRecords.get(0).value()).contains("{{{{invalid json");
            // → DLQ는 버리는 곳이 아니라 나중에 확인/재처리할 수 있는 격리 공간
        }
    }
}
