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
 * 서버 재시작 후에도 PENDING 이벤트가 DB에 남아있어서 재처리 가능함을 검증한다.
 * "서버 재시작"을 직접 시뮬레이션하지 않고, 핵심 속성(DB 영속성)을 검증한다.
 */
@SpringBootTest(classes = Step3TestConfig.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EventStoreRecoveryTest {

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
    void 서버_재시작_후에도_PENDING_이벤트는_DB에_남아있다() {
        // Given: 주문 생성 → PENDING 이벤트 기록
        orderEventStoreService.createOrder("노트북", 1_500_000L);
        // 릴레이를 실행하지 않음 (서버가 죽은 것처럼)

        // Then: DB에 PENDING 이벤트가 살아있다
        List<EventRecord> pending = eventRecordRepository.findByStatus(EventStatus.PENDING);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getEventType()).isEqualTo("ORDER_CREATED");

        // Step 2의 @Async 방식이었다면, 이 이벤트는 메모리에서 사라졌을 것이다
        // Event Store 덕분에 DB에 영속되어 재처리가 가능하다
    }

    @Test
    void 재시작_후_스케줄러가_PENDING_이벤트를_재처리한다() {
        // Given: 여러 주문 생성 후 릴레이 미실행 (서버 다운 시뮬레이션)
        Long orderId1 = orderEventStoreService.createOrder("노트북", 1_500_000L);
        Long orderId2 = orderEventStoreService.createOrder("키보드", 200_000L);

        assertThat(eventRecordRepository.findByStatus(EventStatus.PENDING)).hasSize(2);
        assertThat(pointRecordRepository.findAll()).isEmpty();

        // When: "재시작된 서버"의 스케줄러가 릴레이 실행
        int processed = eventRelay.processEvents();

        // Then: 모든 PENDING 이벤트가 처리되었다
        assertThat(processed).isEqualTo(2);
        assertThat(eventRecordRepository.findByStatus(EventStatus.PENDING)).isEmpty();
        assertThat(eventRecordRepository.findByStatus(EventStatus.PROCESSED)).hasSize(2);

        // 포인트도 모두 적립되었다
        assertThat(pointRecordRepository.findByOrderId(orderId1)).isPresent();
        assertThat(pointRecordRepository.findByOrderId(orderId2)).isPresent();
    }
}
