package com.example.messaging.step3_event_store.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "step3_points")
public class PointRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private long amount;

    protected PointRecord() {
    }

    public PointRecord(Long orderId, long amount) {
        this.orderId = orderId;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public long getAmount() {
        return amount;
    }
}
