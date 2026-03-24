package com.example.messaging.step2_transactional_event;

import com.example.messaging.step2_transactional_event.listener.PhaseTestListener;
import com.example.messaging.step2_transactional_event.repository.OrderRepository;
import com.example.messaging.step2_transactional_event.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @TransactionalEventListener의 4가지 phase를 검증한다.
 * BEFORE_COMMIT의 위험, AFTER_ROLLBACK의 용도, AFTER_COMPLETION의 동작.
 */
@SpringBootTest(classes = Step2TestConfig.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TransactionalEventListenerPhaseTest {

    @Autowired
    OrderService orderService;

    @Autowired
    PhaseTestListener phaseTestListener;

    @Autowired
    OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        phaseTestListener.reset();
        orderRepository.deleteAll();
    }

    @Test
    void BEFORE_COMMIT_리스너에서_예외_발생_시_발행자_TX가_롤백된다() {
        // Given: BEFORE_COMMIT 리스너가 예외를 던지도록 설정
        phaseTestListener.setBeforeCommitShouldFail(true);

        // When & Then: BEFORE_COMMIT은 아직 TX 안이므로 예외가 발행자 TX를 롤백시킨다
        assertThatThrownBy(() -> orderService.createOrder("user-1", 50_000L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("BEFORE_COMMIT 리스너 실패");

        // 주문도 롤백됨 — @EventListener와 같은 위험!
        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void AFTER_ROLLBACK_리스너는_롤백_후에만_실행된다() {
        // When: 주문 생성 중 롤백 발생
        assertThatThrownBy(() -> orderService.createOrderThatWillFail("user-1", 50_000L))
                .isInstanceOf(RuntimeException.class);

        // Then: AFTER_ROLLBACK 리스너가 실행됨
        assertThat(phaseTestListener.isAfterRollbackExecuted()).isTrue();
    }

    @Test
    void AFTER_COMPLETION_리스너는_커밋_롤백_무관하게_실행된다() {
        // Case 1: 커밋 성공
        orderService.createOrder("user-1", 50_000L);
        assertThat(phaseTestListener.isAfterCompletionExecuted()).isTrue();

        phaseTestListener.reset();

        // Case 2: 롤백
        assertThatThrownBy(() -> orderService.createOrderThatWillFail("user-2", 50_000L))
                .isInstanceOf(RuntimeException.class);
        assertThat(phaseTestListener.isAfterCompletionExecuted()).isTrue();
    }
}
