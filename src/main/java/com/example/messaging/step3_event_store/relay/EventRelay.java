package com.example.messaging.step3_event_store.relay;

import com.example.messaging.step3_event_store.domain.EventRecord;
import com.example.messaging.step3_event_store.domain.EventStatus;
import com.example.messaging.step3_event_store.repository.EventRecordRepository;
import com.example.messaging.step3_event_store.service.PointService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PENDING 이벤트를 조회하여 처리하고 PROCESSED로 상태를 변경하는 릴레이.
 * 실제로는 @Scheduled로 주기적 실행하지만, 테스트에서는 직접 호출한다.
 */
@Component
public class EventRelay {

    private final EventRecordRepository eventRecordRepository;
    private final PointService pointService;
    private final ObjectMapper objectMapper;

    public EventRelay(EventRecordRepository eventRecordRepository,
                      PointService pointService,
                      ObjectMapper objectMapper) {
        this.eventRecordRepository = eventRecordRepository;
        this.pointService = pointService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int processEvents() {
        List<EventRecord> pendingEvents = eventRecordRepository.findByStatus(EventStatus.PENDING);
        for (EventRecord event : pendingEvents) {
            processEvent(event);
            event.markProcessed();
            eventRecordRepository.save(event);
        }
        return pendingEvents.size();
    }

    private void processEvent(EventRecord event) {
        if ("ORDER_CREATED".equals(event.getEventType())) {
            try {
                JsonNode node = objectMapper.readTree(event.getPayload());
                long orderId = node.get("orderId").asLong();
                long amount = node.get("amount").asLong();
                pointService.addPoint(orderId, amount);
            } catch (Exception e) {
                throw new RuntimeException("이벤트 처리 실패: " + event.getId(), e);
            }
        }
    }
}
