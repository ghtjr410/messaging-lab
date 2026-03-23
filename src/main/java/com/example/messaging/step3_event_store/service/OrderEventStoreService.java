package com.example.messaging.step3_event_store.service;

import com.example.messaging.step3_event_store.domain.EventRecord;
import com.example.messaging.step3_event_store.domain.Order;
import com.example.messaging.step3_event_store.repository.EventRecordRepository;
import com.example.messaging.step3_event_store.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 저장과 이벤트 기록을 하나의 트랜잭션으로 묶는다.
 * 둘 다 성공하거나, 둘 다 실패한다.
 */
@Service
public class OrderEventStoreService {

    private final OrderRepository orderRepository;
    private final EventRecordRepository eventRecordRepository;

    public OrderEventStoreService(OrderRepository orderRepository,
                                  EventRecordRepository eventRecordRepository) {
        this.orderRepository = orderRepository;
        this.eventRecordRepository = eventRecordRepository;
    }

    @Transactional
    public Long createOrder(String productName, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("주문 금액은 0보다 커야 합니다");
        }

        Order order = new Order(productName, amount);
        orderRepository.save(order);

        String payload = String.format(
                "{\"orderId\":%d,\"productName\":\"%s\",\"amount\":%d}",
                order.getId(), productName, amount);

        eventRecordRepository.save(new EventRecord("ORDER_CREATED", payload));

        return order.getId();
    }
}
