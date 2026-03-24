package com.example.messaging.step2_transactional_event.listener;

import com.example.messaging.step2_transactional_event.event.OrderCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AFTER_COMMIT에서 DB 저장 시, REQUIRES_NEW로 해결하는 방법을 보여주는 리스너.
 *
 * 핵심: @Transactional(REQUIRES_NEW) 메서드를 같은 클래스에 두면
 * this.method() 호출이 되어 Spring AOP 프록시를 거치지 않는다.
 * 반드시 별도 빈(PointSaveService)으로 분리해야 한다.
 *
 * enabled=false 시 이벤트를 무시한다 (다른 테스트와의 격리).
 */
@Component
public class AfterCommitDbSaveListener {

    private final PointSaveService pointSaveService;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicBoolean useRequiresNew = new AtomicBoolean(false);
    private final AtomicBoolean executed = new AtomicBoolean(false);
    private final AtomicBoolean txActiveInHandler = new AtomicBoolean(false);

    public AfterCommitDbSaveListener(PointSaveService pointSaveService) {
        this.pointSaveService = pointSaveService;
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
            // 별도 빈을 통해 호출 → Spring 프록시가 REQUIRES_NEW를 적용한다
            pointSaveService.savePoint(event.userId(), event.amount());
        }
        // useRequiresNew=false면 저장하지 않음 (TX가 없어서 안전하지 않으므로)
    }
}
