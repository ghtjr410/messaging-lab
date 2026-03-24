package com.example.messaging.step2_transactional_event.service;

import com.example.messaging.step2_transactional_event.domain.Order;
import com.example.messaging.step2_transactional_event.event.OrderCreatedEvent;
import com.example.messaging.step2_transactional_event.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * @Transactional이 없는 서비스.
 * 여기서 이벤트를 발행하면 @TransactionalEventListener가 불리지 않는다.
 */
@Service
public class NonTransactionalOrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NonTransactionalOrderService(OrderRepository orderRepository,
                                         ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    // @Transactional이 없다!
    public Long createOrderWithoutTx(String userId, long amount) {
        Order order = new Order(userId, amount);
        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId(), userId, amount, Instant.now()));

        return order.getId();
    }
}
