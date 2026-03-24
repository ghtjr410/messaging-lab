package com.example.messaging.step7_idempotent_consumer.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "step7_event_handled")
public class EventHandled {

    @Id
    private String eventId;

    @Column(nullable = false)
    private LocalDateTime handledAt;

    protected EventHandled() {
    }

    public EventHandled(String eventId) {
        this.eventId = eventId;
        this.handledAt = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }
}
