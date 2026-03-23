package com.example.messaging.step2_transactional_event;

import com.example.messaging.step2_transactional_event.listener.AsyncTransactionalPointListener;
import com.example.messaging.step2_transactional_event.repository.OrderRepository;
import com.example.messaging.step2_transactional_event.repository.PointRepository;
import com.example.messaging.step2_transactional_event.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Async 이벤트가 메모리에만 존재하므로, 서버가 죽으면 유실되는 한계를 체험한다.
 * 이 한계가 Step 3(Event Store)로 넘어가는 동기가 된다.
 */
@SpringBootTest(classes = Step2TestConfig.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AsyncEventLossTest {

    @Autowired
    OrderService orderService;

    @Autowired
    AsyncTransactionalPointListener asyncPointListener;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PointRepository pointRepository;

    @AfterEach
    void tearDown() {
        asyncPointListener.reset();
        orderRepository.deleteAll();
        pointRepository.deleteAll();
    }

    /**
     * 흐름:
     *   리스너가 예외를 던지도록 설정 → 주문 생성 (커밋 완료)
     *   → @Async 리스너 실행 → 예외 발생 → 포인트 미적립
     *   → 이벤트 기록이 어디에도 없으므로 재처리 방법 없음
     *
     * 증명: @Async 이벤트는 메모리에만 존재하므로, 실패해도 재처리할 수 없다.
     *       DB에 PENDING 기록이 없기 때문이다.
     *       Step 3의 Event Store가 필요한 이유.
     */
    @Test
    void 서버가_재시작되면_Async_리스너가_처리하지_못한_이벤트는_유실된다() throws InterruptedException {
        // Given: 리스너가 실패하도록 설정 (서버 재시작 중 처리 실패와 동일한 효과)
        asyncPointListener.setShouldFail(true);
        CountDownLatch latch = new CountDownLatch(1);
        asyncPointListener.setLatch(latch);

        // When: 주문 생성 (커밋 완료 — 주문은 DB에 안전하게 저장됨)
        Long orderId = orderService.createOrder("user-1", 50_000L);
        latch.await(5, TimeUnit.SECONDS);

        // Then: 주문은 있지만 포인트는 없다
        assertThat(orderRepository.findById(orderId)).isPresent();
        assertThat(pointRepository.findByUserId("user-1")).isEmpty();

        // 핵심: 이 이벤트를 다시 처리할 방법이 없다!
        // - DB에 "PENDING" 이벤트 레코드가 없다 (Step 3과의 차이)
        // - 메모리에 있던 이벤트는 이미 사라졌다
        // - 어떤 스케줄러도 이 이벤트를 다시 찾을 수 없다
        //
        // Step 3에서는 같은 상황에서 Event Store(DB)에 PENDING 레코드가 남아
        // 스케줄러가 재처리할 수 있다.
    }
}
