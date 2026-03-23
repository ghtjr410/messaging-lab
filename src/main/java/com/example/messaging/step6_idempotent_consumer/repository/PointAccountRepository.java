package com.example.messaging.step6_idempotent_consumer.repository;

import com.example.messaging.step6_idempotent_consumer.domain.PointAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {
    Optional<PointAccount> findByUserId(String userId);
}
