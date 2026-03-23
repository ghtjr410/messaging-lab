package com.example.messaging.step6_idempotent_consumer.repository;

import com.example.messaging.step6_idempotent_consumer.domain.EventHandled;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledRepository extends JpaRepository<EventHandled, String> {
}
