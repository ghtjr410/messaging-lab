package com.example.messaging.step2_transactional_event.listener;

import com.example.messaging.step2_transactional_event.domain.Point;
import com.example.messaging.step2_transactional_event.repository.PointRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AFTER_COMMIT 리스너에서 DB 저장이 필요할 때 사용하는 별도 빈.
 *
 * AFTER_COMMIT 시점에 기존 TX는 이미 커밋되었으므로,
 * REQUIRES_NEW로 새 TX를 열어야 안전하게 DB에 쓸 수 있다.
 *
 * 같은 클래스 내부에서 this.method()로 호출하면 Spring AOP 프록시를 거치지 않아
 * @Transactional이 무시된다. 반드시 별도 빈으로 분리해야 프록시가 작동한다.
 */
@Component
public class PointSaveService {

    private final PointRepository pointRepository;

    public PointSaveService(PointRepository pointRepository) {
        this.pointRepository = pointRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePoint(String userId, long amount) {
        long pointAmount = (long) (amount * 0.01);
        pointRepository.save(new Point(userId, pointAmount));
    }
}
