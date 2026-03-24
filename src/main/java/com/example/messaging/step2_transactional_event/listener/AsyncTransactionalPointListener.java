package com.example.messaging.step2_transactional_event.listener;

import com.example.messaging.step2_transactional_event.domain.Point;
import com.example.messaging.step2_transactional_event.event.OrderCreatedEvent;
import com.example.messaging.step2_transactional_event.repository.PointRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @Async + @TransactionalEventListener(AFTER_COMMIT):
 * 별도 스레드에서 커밋 후 실행. 빠르지만 실패가 보이지 않는다.
 */
@Component
public class AsyncTransactionalPointListener {

    private final PointRepository pointRepository;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean shouldFail = new AtomicBoolean(false);
    private final AtomicReference<String> executedThread = new AtomicReference<>();
    private volatile CountDownLatch latch;

    public AsyncTransactionalPointListener(PointRepository pointRepository) {
        this.pointRepository = pointRepository;
    }

    public void setShouldFail(boolean fail) {
        this.shouldFail.set(fail);
    }

    public void setLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    public String getExecutedThread() {
        return executedThread.get();
    }

    public void setEnabled(boolean on) {
        this.enabled.set(on);
    }

    public void reset() {
        enabled.set(true);
        shouldFail.set(false);
        executedThread.set(null);
        latch = null;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCreatedEvent event) {
        if (!enabled.get()) {
            if (latch != null) latch.countDown();
            return;
        }
        executedThread.set(Thread.currentThread().getName());
        try {
            if (shouldFail.get()) {
                throw new RuntimeException("포인트 적립 실패 (Async)");
            }
            long pointAmount = (long) (event.amount() * 0.01);
            pointRepository.save(new Point(event.userId(), pointAmount));
        } finally {
            if (latch != null) {
                latch.countDown();
            }
        }
    }
}
