package com.example.messaging.step6_idempotent_consumer.consumer;

import com.example.messaging.step6_idempotent_consumer.domain.PointAccount;
import com.example.messaging.step6_idempotent_consumer.repository.PointAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멱등 처리 없는 단순 Consumer. 같은 이벤트가 2번 오면 2번 적립한다.
 */
@Component
public class NaivePointConsumer {

    private final PointAccountRepository pointAccountRepository;

    public NaivePointConsumer(PointAccountRepository pointAccountRepository) {
        this.pointAccountRepository = pointAccountRepository;
    }

    @Transactional
    public void consume(String eventId, String userId, long points) {
        PointAccount account = pointAccountRepository.findByUserId(userId)
                .orElseGet(() -> pointAccountRepository.save(new PointAccount(userId, 0)));
        account.addBalance(points);
        pointAccountRepository.save(account);
    }
}
