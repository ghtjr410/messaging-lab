package com.example.messaging.step2_transactional_event.listener;

import com.example.messaging.step2_transactional_event.domain.Point;
import com.example.messaging.step2_transactional_event.event.OrderCreatedEvent;
import com.example.messaging.step2_transactional_event.repository.PointRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @TransactionalEventListener(AFTER_COMMIT): 트랜잭션 커밋 후에만 실행된다.
 * 롤백되면 실행되지 않으므로 안전하다.
 */
@Component
public class TransactionalPointListener {

    private final PointRepository pointRepository;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean shouldFail = new AtomicBoolean(false);
    private final AtomicBoolean executed = new AtomicBoolean(false);
    private Runnable callback;

    public TransactionalPointListener(PointRepository pointRepository) {
        this.pointRepository = pointRepository;
    }

    public void setShouldFail(boolean fail) {
        this.shouldFail.set(fail);
    }

    public void setCallback(Runnable callback) {
        this.callback = callback;
    }

    public boolean isExecuted() {
        return executed.get();
    }

    public void setEnabled(boolean on) {
        this.enabled.set(on);
    }

    public void reset() {
        enabled.set(true);
        shouldFail.set(false);
        executed.set(false);
        callback = null;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCreatedEvent event) {
        if (!enabled.get()) return;
        executed.set(true);
        if (callback != null) {
            callback.run();
        }
        if (shouldFail.get()) {
            throw new RuntimeException("포인트 적립 실패 (TransactionalEventListener)");
        }
        long pointAmount = (long) (event.amount() * 0.01);
        pointRepository.save(new Point(event.userId(), pointAmount));
    }
}
