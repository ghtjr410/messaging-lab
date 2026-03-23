package com.example.messaging.step3_event_store.service;

import com.example.messaging.step3_event_store.domain.PointRecord;
import com.example.messaging.step3_event_store.repository.PointRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {

    private final PointRecordRepository pointRecordRepository;

    public PointService(PointRecordRepository pointRecordRepository) {
        this.pointRecordRepository = pointRecordRepository;
    }

    @Transactional
    public void addPoint(Long orderId, long orderAmount) {
        long pointAmount = (long) (orderAmount * 0.01);
        pointRecordRepository.save(new PointRecord(orderId, pointAmount));
    }
}
