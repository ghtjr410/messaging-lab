package com.example.messaging.step0_command_vs_event;

import com.example.messaging.step0_command_vs_event.command.CreateOrderCommand;
import com.example.messaging.step0_command_vs_event.command.IssueCouponCommand;
import com.example.messaging.step0_command_vs_event.domain.Order;
import com.example.messaging.step0_command_vs_event.event.OrderCreatedEvent;
import com.example.messaging.step0_command_vs_event.handler.CouponCommandHandler;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Command와 Event의 행동 차이를 확인한다.
 * Command는 실패할 수 있고, Event는 이미 일어난 사실이다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CommandEventBehaviorTest {

    @Test
    void Command는_실패할_수_있고_발신자가_처리해야_한다() {
        // Given: 재고가 없는 쿠폰 타입
        CouponCommandHandler handler = new CouponCommandHandler();
        handler.setStock("WELCOME", 0);  // 재고 소진

        IssueCouponCommand command = new IssueCouponCommand("user-1", "WELCOME");

        // When & Then: Command는 실패할 수 있다
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고 소진");
        // 발신자는 이 실패를 알아야 하고, 대응해야 한다
    }

    @Test
    void Event는_이미_일어난_사실이므로_발행_자체는_실패하지_않는다() {
        // Given: 이미 확정된 사실
        OrderCreatedEvent event = OrderCreatedEvent.of("order-1", "user-1", 50_000L);

        // When: Event를 발행한다 (여기서는 단순 리스트에 추가)
        List<OrderCreatedEvent> publishedEvents = new ArrayList<>();
        publishedEvents.add(event);

        // Then: 발행 자체는 항상 성공한다 (이미 일어난 사실의 기록이니까)
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0).orderId()).isEqualTo("order-1");
        // 리스너가 처리에 실패하더라도, 그건 리스너의 문제이지 발행자의 문제가 아니다
    }

    @Test
    void 같은_도메인에서_Command_실행_결과가_Event가_된다() {
        // "주문을 생성해라" — Command (아직 안 일어남)
        CreateOrderCommand command = new CreateOrderCommand("user-1", 50_000L);

        // 주문 생성 로직 실행 (실패할 수 있음)
        Order order = Order.create(command.userId(), command.amount());

        // "주문이 생성되었다" — Event (이제 확정된 사실)
        OrderCreatedEvent event = OrderCreatedEvent.of(
                order.getOrderId(), order.getUserId(), order.getAmount());

        // Command는 "해라"라는 지시, Event는 "됐다"라는 사실
        assertThat(event.occurredAt()).isNotNull();           // 사실 — 시각이 찍혔다
        assertThat(event.eventId()).isNotNull();               // 고유 식별자
        assertThat(event.orderId()).isEqualTo(order.getOrderId());  // Command 실행 결과가 Event에 담긴다
        assertThat(event.amount()).isEqualTo(command.amount());
    }
}
