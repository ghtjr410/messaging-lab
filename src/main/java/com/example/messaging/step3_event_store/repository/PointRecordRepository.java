package com.example.messaging.step3_event_store.repository;

import com.example.messaging.step3_event_store.domain.PointRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointRecordRepository extends JpaRepository<PointRecord, Long> {
    Optional<PointRecord> findByOrderId(Long orderId);
}
