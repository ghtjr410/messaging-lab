package com.example.messaging.step6_idempotent_consumer.consumer;

import com.example.messaging.step6_idempotent_consumer.domain.EventHandled;
import com.example.messaging.step6_idempotent_consumer.domain.PointAccount;
import com.example.messaging.step6_idempotent_consumer.repository.EventHandledRepository;
import com.example.messaging.step6_idempotent_consumer.repository.PointAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * event_handled 테이블로 중복을 방어하는 Consumer.
 * event_id가 이미 처리되었으면 스킵한다.
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
        if (eventHandledRepository.existsById(eventId)) {
            return false; // 이미 처리됨 → 스킵
        }

        PointAccount account = pointAccountRepository.findByUserId(userId)
                .orElseGet(() -> pointAccountRepository.save(new PointAccount(userId, 0)));
        account.addBalance(points);
        pointAccountRepository.save(account);

        eventHandledRepository.save(new EventHandled(eventId));
        return true;
    }
}
