package com.example.messaging.step3_event_store;

import com.example.messaging.step3_event_store.domain.EventRecord;
import com.example.messaging.step3_event_store.domain.EventStatus;
import com.example.messaging.step3_event_store.relay.EventRelay;
import com.example.messaging.step3_event_store.repository.EventRecordRepository;
import com.example.messaging.step3_event_store.repository.OrderRepository;
import com.example.messaging.step3_event_store.repository.PointRecordRepository;
import com.example.messaging.step3_event_store.service.OrderEventStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러(릴레이)가 PENDING 이벤트를 처리하는 흐름을 검증한다.
 */
@SpringBootTest(classes = Step3TestConfig.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EventRelayTest {

    @Autowired
    OrderEventStoreService orderEventStoreService;

    @Autowired
    EventRelay eventRelay;

    @Autowired
    EventRecordRepository eventRecordRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PointRecordRepository pointRecordRepository;

    @AfterEach
    void tearDown() {
        pointRecordRepository.deleteAll();
        eventRecordRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void 스케줄러는_PENDING_상태의_이벤트를_조회하여_처리한다() {
        // Given: 주문 생성 → PENDING 이벤트 기록됨
        Long orderId = orderEventStoreService.createOrder("노트북", 1_500_000L);

        // When: 릴레이 실행
        int processed = eventRelay.processEvents();

        // Then: 이벤트가 처리되어 포인트가 적립되었다
        assertThat(processed).isEqualTo(1);
        assertThat(pointRecordRepository.findByOrderId(orderId)).isPresent();
        assertThat(pointRecordRepository.findByOrderId(orderId).get().getAmount()).isEqualTo(15_000L);
    }

    @Test
    void 처리_완료된_이벤트는_PROCESSED_상태로_변경된다() {
        // Given
        orderEventStoreService.createOrder("노트북", 1_500_000L);

        // When
        eventRelay.processEvents();

        // Then
        List<EventRecord> pending = eventRecordRepository.findByStatus(EventStatus.PENDING);
        List<EventRecord> processed = eventRecordRepository.findByStatus(EventStatus.PROCESSED);

        assertThat(pending).isEmpty();
        assertThat(processed).hasSize(1);
        assertThat(processed.get(0).getEventType()).isEqualTo("ORDER_CREATED");
    }

    @Test
    void 이미_처리된_이벤트는_다시_처리하지_않는다() {
        // Given
        Long orderId = orderEventStoreService.createOrder("노트북", 1_500_000L);
        eventRelay.processEvents();

        // When: 릴레이를 다시 실행
        int secondRun = eventRelay.processEvents();

        // Then: 추가 처리 없음
        assertThat(secondRun).isEqualTo(0);
        // 포인트도 1건만 존재 (중복 적립 없음)
        assertThat(pointRecordRepository.findAll()).hasSize(1);
    }
}
