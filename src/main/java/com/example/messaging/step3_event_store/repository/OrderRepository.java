package com.example.messaging.step3_event_store.repository;

import com.example.messaging.step3_event_store.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
