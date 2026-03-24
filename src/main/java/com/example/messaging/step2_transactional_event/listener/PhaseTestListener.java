package com.example.messaging.step2_transactional_event.listener;

import com.example.messaging.step2_transactional_event.event.OrderCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 4개 phase를 각각 별도 메서드로 구현하여 실행 여부를 테스트한다.
 */
@Component
public class PhaseTestListener {

    private final AtomicBoolean beforeCommitExecuted = new AtomicBoolean(false);
    private final AtomicBoolean afterRollbackExecuted = new AtomicBoolean(false);
    private final AtomicBoolean afterCompletionExecuted = new AtomicBoolean(false);
    private final AtomicBoolean beforeCommitShouldFail = new AtomicBoolean(false);

    public void setBeforeCommitShouldFail(boolean fail) {
        this.beforeCommitShouldFail.set(fail);
    }

    public boolean isBeforeCommitExecuted() {
        return beforeCommitExecuted.get();
    }

    public boolean isAfterRollbackExecuted() {
        return afterRollbackExecuted.get();
    }

    public boolean isAfterCompletionExecuted() {
        return afterCompletionExecuted.get();
    }

    public void reset() {
        beforeCommitExecuted.set(false);
        afterRollbackExecuted.set(false);
        afterCompletionExecuted.set(false);
        beforeCommitShouldFail.set(false);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleBeforeCommit(OrderCreatedEvent event) {
        beforeCommitExecuted.set(true);
        if (beforeCommitShouldFail.get()) {
            throw new RuntimeException("BEFORE_COMMIT 리스너 실패");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleAfterRollback(OrderCreatedEvent event) {
        afterRollbackExecuted.set(true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void handleAfterCompletion(OrderCreatedEvent event) {
        afterCompletionExecuted.set(true);
    }
}
