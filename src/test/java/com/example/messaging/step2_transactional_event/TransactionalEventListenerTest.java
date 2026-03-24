package com.example.messaging.step2_transactional_event;

import com.example.messaging.step2_transactional_event.domain.Order;
import com.example.messaging.step2_transactional_event.domain.OrderStatus;
import com.example.messaging.step2_transactional_event.listener.TransactionalPointListener;
import com.example.messaging.step2_transactional_event.repository.OrderRepository;
import com.example.messaging.step2_transactional_event.repository.PointRepository;
import com.example.messaging.step2_transactional_event.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @TransactionalEventListener(AFTER_COMMIT)의 안전한 타이밍을 확인한다.
 * 커밋 후에만 실행되므로 롤백 시 리스너가 실행되지 않는다.
 */
@SpringBootTest(classes = Step2TestConfig.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TransactionalEventListenerTest {

    @Autowired
    OrderService orderService;

    @Autowired
    TransactionalPointListener transactionalPointListener;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PointRepository pointRepository;

    @AfterEach
    void tearDown() {
        transactionalPointListener.reset();
        orderRepository.deleteAll();
        pointRepository.deleteAll();
    }

    @Nested
    class 안전한_타이밍_커밋_후에만_실행된다 {

        @Test
        void TransactionalEventListener는_커밋_후에만_실행된다() {
            // When: 주문 생성 (트랜잭션 커밋됨)
            Long orderId = orderService.createOrder("user-1", 50_000L);

            // Then: 커밋 후 리스너가 실행되었다
            assertThat(transactionalPointListener.isExecuted()).isTrue();
            assertThat(orderRepository.findById(orderId)).isPresent();
        }

        @Test
        void 트랜잭션이_롤백되면_TransactionalEventListener는_실행되지_않는다() {
            // When: 주문 생성 중 롤백 발생
            assertThatThrownBy(() -> orderService.createOrderThatWillFail("user-1", 50_000L))
                    .isInstanceOf(RuntimeException.class);

            // Then: 리스너는 실행되지 않았다! (안전)
            assertThat(transactionalPointListener.isExecuted()).isFalse();
            assertThat(orderRepository.findAll()).isEmpty();
        }
    }

    @Nested
    class Step1_한계_해결_리스너_예외가_발행자_TX를_롤백시키지_않는다 {

        @Test
        void TransactionalEventListener_예외는_발행자_트랜잭션에_영향을_주지_않는다() {
            // Given: 리스너가 예외를 던지도록 설정
            transactionalPointListener.setShouldFail(true);

            // When: 주문 생성 — 리스너 예외가 발생하지만...
            Long orderId = orderService.createOrder("user-1", 50_000L);

            // Then: 주문은 이미 커밋되었으므로 안전하다!
            Order order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

            // 포인트는 적립되지 않았지만, 주문은 살아있다
            // Step 1의 한계가 해결되었다
        }
    }
}
