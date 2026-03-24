package com.example.messaging.step7_idempotent_consumer.consumer;

import com.example.messaging.step7_idempotent_consumer.domain.EventHandled;
import com.example.messaging.step7_idempotent_consumer.domain.PointAccount;
import com.example.messaging.step7_idempotent_consumer.repository.EventHandledRepository;
import com.example.messaging.step7_idempotent_consumer.repository.PointAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * event_handled 테이블로 중복을 방어하는 Consumer.
 *
 * 동시에 같은 eventId가 들어와도 PK 중복 예외로 하나만 처리된다.
 * existsById 체크는 fast-path 최적화이고, 실제 안전장치는 DB PK constraint다.
 */
@Component
public class IdempotentPointConsumer {

    private final PointAccountRepository pointAccountRepository;
    private final EventHandledRepository eventHandledRepository;

    public IdempotentPointConsumer(PointAccountRepository pointAccountRepository,
                                  EventHandledRepository eventHandledRepository) {
        this.pointAccountRepository = pointAccountRepository;
        this.eventHandledRepository = eventHandledRepository;
    }

    @Transactional
    public boolean consume(String eventId, String userId, long points) {
        // fast-path: 이미 처리된 이벤트는 DB 조회 없이 빠르게 스킵
        if (eventHandledRepository.existsById(eventId)) {
            return false;
        }

        // 처리 기록을 먼저 삽입 — PK 중복 시 예외로 동시성 방어
        try {
            eventHandledRepository.saveAndFlush(new EventHandled(eventId));
        } catch (DataIntegrityViolationException e) {
            // 다른 스레드가 먼저 처리함 → 스킵
            return false;
        }

        PointAccount account = pointAccountRepository.findByUserId(userId)
                .orElseGet(() -> pointAccountRepository.save(new PointAccount(userId, 0)));
        account.addBalance(points);
        pointAccountRepository.save(account);

        return true;
    }
}
