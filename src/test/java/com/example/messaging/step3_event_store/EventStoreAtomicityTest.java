package com.example.messaging.step3_event_store;

import com.example.messaging.step3_event_store.domain.EventRecord;
import com.example.messaging.step3_event_store.domain.EventStatus;
import com.example.messaging.step3_event_store.domain.Order;
import com.example.messaging.step3_event_store.domain.OrderStatus;
import com.example.messaging.step3_event_store.repository.EventRecordRepository;
import com.example.messaging.step3_event_store.repository.OrderRepository;
import com.example.messaging.step3_event_store.service.OrderEventStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 도메인 저장과 이벤트 기록의 원자성을 검증한다.
 * 둘 다 성공하거나, 둘 다 실패해야 한다.
 */
@SpringBootTest(classes = Step3TestConfig.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EventStoreAtomicityTest {

    @Autowired
    OrderEventStoreService orderEventStoreService;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    EventRecordRepository eventRecordRepository;

    @AfterEach
    void tearDown() {
        eventRecordRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void 주문_저장과_이벤트_기록은_하나의_트랜잭션으로_묶인다() {
        // When
        Long orderId = orderEventStoreService.createOrder("노트북", 1_500_000L);

        // Then: 주문이 저장되었다
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getProductName()).isEqualTo("노트북");

        // Then: 이벤트도 같은 트랜잭션으로 기록되었다
        List<EventRecord> events = eventRecordRepository.findByStatus(EventStatus.PENDING);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(events.get(0).getPayload()).contains("노트북");
        assertThat(events.get(0).getPayload()).contains(orderId.toString());
    }

    @Test
    void 주문_저장이_실패하면_이벤트_기록도_함께_롤백된다() {
        // When: 유효하지 않은 금액으로 주문 시도
        assertThatThrownBy(() -> orderEventStoreService.createOrder("노트북", -1))
                .isInstanceOf(IllegalArgumentException.class);

        // Then: 주문도 이벤트도 모두 저장되지 않았다
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(eventRecordRepository.findAll()).isEmpty();
        // → 하나의 트랜잭션이므로 전부 롤백
    }
}
