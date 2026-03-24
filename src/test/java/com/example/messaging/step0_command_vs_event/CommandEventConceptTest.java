package com.example.messaging.step0_command_vs_event;

import com.example.messaging.step0_command_vs_event.command.IssueCouponCommand;
import com.example.messaging.step0_command_vs_event.domain.Coupon;
import com.example.messaging.step0_command_vs_event.event.OrderCreatedEvent;
import com.example.messaging.step0_command_vs_event.handler.CouponCommandHandler;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Command와 Event의 구조적 차이를 순수 Java 객체로 확인한다.
 * Spring 컨텍스트 없이 실행되며, 개념이 인프라에 독립적임을 보여준다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CommandEventConceptTest {

    @Test
    void Command는_미래시제다_아직_일어나지_않은_일() {
        // Command: "쿠폰을 발급해라" — 결과가 아직 없다
        IssueCouponCommand command = new IssueCouponCommand("user-1", "WELCOME");

        assertThat(command.userId()).isEqualTo("user-1");
        assertThat(command.couponType()).isEqualTo("WELCOME");
        // Command의 타임스탬프는 requestedAt(요청 시각)이다.
        // Event의 occurredAt(확정 시각)과 의미가 다르다.
        assertThat(command.requestedAt()).isNotNull();
        assertThat(command.commandId()).isNotNull();
    }

    @Test
    void Event는_과거시제다_이미_확정된_사실() {
        // Event: "주문이 생성되었다" — 이미 확정된 사실
        OrderCreatedEvent event = OrderCreatedEvent.of("order-1", "user-1", 50_000L);

        assertThat(event.orderId()).isEqualTo("order-1");
        assertThat(event.occurredAt()).isNotNull();  // Event에는 확정 시각이 있다 — 이미 일어났으니까
        assertThat(event.eventId()).isNotNull();
    }

    @Test
    void Command는_수신자를_특정한다_1대1() {
        // Command는 "누가" 처리할지 발신자가 안다
        IssueCouponCommand command = new IssueCouponCommand("user-1", "WELCOME");
        CouponCommandHandler handler = new CouponCommandHandler();

        // 발신자가 직접 handler를 호출한다 — 1:1 관계
        Coupon coupon = handler.handle(command);

        assertThat(coupon).isNotNull();
        assertThat(coupon.getUserId()).isEqualTo("user-1");
    }

    @Test
    void Event는_수신자를_모른다_1대N() {
        // Event는 발행만 하고, 누가 듣는지 모른다
        OrderCreatedEvent event = OrderCreatedEvent.of("order-1", "user-1", 50_000L);

        List<String> reactedListeners = new ArrayList<>();

        // 리스너가 몇 개든 발행자 코드는 동일
        List<Consumer<OrderCreatedEvent>> listeners = List.of(
                e -> reactedListeners.add("coupon"),
                e -> reactedListeners.add("point"),
                e -> reactedListeners.add("notification")
        );

        listeners.forEach(listener -> listener.accept(event));

        assertThat(reactedListeners).hasSize(3);
        assertThat(reactedListeners).containsExactly("coupon", "point", "notification");
        // 발행자(Event 생성측)는 이 리스너 목록을 모른다
    }
}
