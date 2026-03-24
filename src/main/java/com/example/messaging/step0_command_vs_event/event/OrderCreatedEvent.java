package com.example.messaging.step0_command_vs_event.event;

import java.time.Instant;
import java.util.UUID;

/**
 * "주문이 생성되었다" — 이미 확정된 사실. 발행자는 누가 듣는지 모른다.
 * eventId: 이벤트 고유 식별자 (Step 7 멱등성에서 사용)
 * occurredAt: 확정된 사실의 시각 (Command의 requestedAt과 의미가 다르다)
 */
public record OrderCreatedEvent(
        String eventId,
        String orderId,
        String userId,
        long amount,
        Instant occurredAt
) {
    public static OrderCreatedEvent of(String orderId, String userId, long amount) {
        return new OrderCreatedEvent(
                UUID.randomUUID().toString(), orderId, userId, amount, Instant.now()
        );
    }
}
