package com.example.messaging.step1_application_event;

import com.example.messaging.step1_application_event.evented.EventedOrderService;
import com.example.messaging.step1_application_event.evented.PointEventListener;
import com.example.messaging.step1_application_event.repository.OrderRepository;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @EventListener의 핵심 한계를 발견한다.
 * 리스너 예외가 발행자 트랜잭션을 롤백시킨다.
 * 이 한계가 Step 2로 넘어가는 동기가 된다.
 */
@SpringBootTest(classes = Step1TestConfig.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EventListenerExceptionTest {

    @Autowired
    EventedOrderService orderService;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PointEventListener pointEventListener;

    @Test
    void 리스너_예외가_발행자_트랜잭션을_롤백시킨다() {
        // Given: PointEventListener가 예외를 던지도록 설정
        pointEventListener.setShouldFail(true);

        // When: 주문 생성 시도
        assertThatThrownBy(() -> orderService.createOrder("user-1", 50_000L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("포인트 적립 실패");

        // Then: 주문까지 롤백되었다!
        assertThat(orderRepository.findAll()).isEmpty();

        // 포인트 적립 실패 때문에 주문이 취소되는 건 우리가 원한 게 아니다
        // → 이 한계를 Step 2에서 해결한다
    }

    @Test
    void EventListener는_발행자와_같은_스레드에서_동기적으로_실행된다() {
        // Given
        pointEventListener.setShouldFail(false);

        // When
        String callerThread = Thread.currentThread().getName();
        orderService.createOrder("user-1", 50_000L);

        // Then: @EventListener는 발행자와 같은 스레드에서 실행된다
        assertThat(pointEventListener.getLastExecutionThread()).isEqualTo(callerThread);
        // 같은 스레드 = 같은 트랜잭션 → 리스너 예외가 발행자 TX를 롤백시키는 원인
    }
}
