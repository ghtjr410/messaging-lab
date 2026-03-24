package com.example.messaging.step7_idempotent_consumer.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "step7_point_accounts")
public class PointAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String userId;

    private long balance;

    protected PointAccount() {
    }

    public PointAccount(String userId, long balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public long getBalance() {
        return balance;
    }

    public void addBalance(long amount) {
        this.balance += amount;
    }
}
