package com.example.messaging.step3_event_store.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "step3_orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productName;
    private long amount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    protected Order() {
    }

    public Order(String productName, long amount) {
        this.productName = productName;
        this.amount = amount;
        this.status = OrderStatus.CREATED;
    }

    public Long getId() {
        return id;
    }

    public String getProductName() {
        return productName;
    }

    public long getAmount() {
        return amount;
    }

    public OrderStatus getStatus() {
        return status;
    }
}
