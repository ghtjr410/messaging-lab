package com.example.messaging.step2_transactional_event.listener;

import com.example.messaging.step2_transactional_event.domain.Point;
import com.example.messaging.step2_transactional_event.event.OrderCreatedEvent;
import com.example.messaging.step2_transactional_event.repository.PointRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AFTER_COMMIT에서 DB 저장 시, TX가 활성 상태인지 확인하고
 * REQUIRES_NEW로 해결하는 방법을 보여주는 리스너.
 *
 * enabled=false 시 이벤트를 무시한다 (다른 테스트와의 격리).
 */
@Component
public class AfterCommitDbSaveListener {

    private final PointRepository pointRepository;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicBoolean useRequiresNew = new AtomicBoolean(false);
    private final AtomicBoolean executed = new AtomicBoolean(false);
    private final AtomicBoolean txActiveInHandler = new AtomicBoolean(false);

    public AfterCommitDbSaveListener(PointRepository pointRepository) {
        this.pointRepository = pointRepository;
    }

    public void enable() {
        this.enabled.set(true);
    }

    public void setUseRequiresNew(boolean useNew) {
        this.useRequiresNew.set(useNew);
    }

    public boolean isExecuted() {
        return executed.get();
    }

    public boolean isTxActiveInHandler() {
        return txActiveInHandler.get();
    }

    public void reset() {
        enabled.set(false);
        useRequiresNew.set(false);
        executed.set(false);
        txActiveInHandler.set(false);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCreatedEvent event) {
        if (!enabled.get()) return;
        executed.set(true);
        txActiveInHandler.set(TransactionSynchronizationManager.isActualTransactionActive());

        if (useRequiresNew.get()) {
            saveWithNewTx(event);
        }
        // useRequiresNew=false면 저장하지 않음 (TX가 없어서 안전하지 않으므로)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveWithNewTx(OrderCreatedEvent event) {
        long pointAmount = (long) (event.amount() * 0.01);
        pointRepository.save(new Point(event.userId(), pointAmount));
    }
}
