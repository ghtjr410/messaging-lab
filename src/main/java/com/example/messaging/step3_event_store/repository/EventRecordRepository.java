package com.example.messaging.step3_event_store.repository;

import com.example.messaging.step3_event_store.domain.EventRecord;
import com.example.messaging.step3_event_store.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRecordRepository extends JpaRepository<EventRecord, Long> {
    List<EventRecord> findByStatus(EventStatus status);
}
