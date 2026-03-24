package com.example.messaging.step1_application_event.evented;

import com.example.messaging.step1_application_event.domain.Point;
import com.example.messaging.step1_application_event.event.OrderCreatedEvent;
import com.example.messaging.step1_application_event.repository.PointRepository;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class PointEventListener {

    private final PointRepository pointRepository;
    private boolean shouldFail = false;

    public PointEventListener(PointRepository pointRepository) {
        this.pointRepository = pointRepository;
    }

    public void setShouldFail(boolean shouldFail) {
        this.shouldFail = shouldFail;
    }

    private String lastExecutionThread;

    public String getLastExecutionThread() {
        return lastExecutionThread;
    }

    @EventListener
    @Order(100) // 다른 리스너보다 나중에 실행
    public void handle(OrderCreatedEvent event) {
        lastExecutionThread = Thread.currentThread().getName();
        if (shouldFail) {
            throw new RuntimeException("포인트 적립 실패");
        }
        long pointAmount = (long) (event.amount() * 0.01);
        pointRepository.save(new Point(event.userId(), pointAmount));
    }
}
