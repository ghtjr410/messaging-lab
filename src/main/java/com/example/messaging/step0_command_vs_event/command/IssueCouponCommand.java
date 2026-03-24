package com.example.messaging.step0_command_vs_event.command;

import java.time.Instant;
import java.util.UUID;

/**
 * "쿠폰을 발급해라" — 아직 일어나지 않은 일. 재고가 없으면 실패한다.
 * commandId: 멱등성 보장 (Step 7과 연결)
 * requestedAt: 요청 시각 (Event의 occurredAt과 의미가 다르다)
 */
public record IssueCouponCommand(
        String commandId,
        String userId,
        String couponType,
        Instant requestedAt
) {
    public IssueCouponCommand(String userId, String couponType) {
        this(UUID.randomUUID().toString(), userId, couponType, Instant.now());
    }
}
