package com.example.messaging.step2_transactional_event;

import com.example.messaging.step2_transactional_event.listener.AfterCommitDbSaveListener;
import com.example.messaging.step2_transactional_event.listener.TransactionalPointListener;
import com.example.messaging.step2_transactional_event.repository.OrderRepository;
import com.example.messaging.step2_transactional_event.repository.PointRepository;
import com.example.messaging.step2_transactional_event.service.NonTransactionalOrderService;
import com.example.messaging.step2_transactional_event.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @TransactionalEventListener + @Async 조합에서 밟기 쉬운 함정 4가지.
 */
@SpringBootTest(classes = Step2TestConfig.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TransactionalEventTrapTest {

    @Autowired
    OrderService orderService;

    @Autowired
    NonTransactionalOrderService nonTxOrderService;

    @Autowired
    TransactionalPointListener transactionalPointListener;

    @Autowired
    AfterCommitDbSaveListener afterCommitDbSaveListener;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PointRepository pointRepository;

    @AfterEach
    void tearDown() {
        transactionalPointListener.reset();
        afterCommitDbSaveListener.reset();
    }

    /**
     * 함정 1: @Transactional이 없으면 @TransactionalEventListener가 불리지 않는다.
     * 트랜잭션의 커밋/롤백을 감지해서 실행되는데, 트랜잭션 자체가 없으면 감지할 게 없다.
     */
    @Test
    void 트랜잭션_없이_이벤트를_발행하면_TransactionalEventListener가_불리지_않는다() {
        // When: @Transactional이 없는 서비스에서 이벤트 발행
        nonTxOrderService.createOrderWithoutTx("user-1", 50_000L);

        // Then: @TransactionalEventListener는 실행되지 않는다!
        assertThat(transactionalPointListener.isExecuted()).isFalse();

        // 주문은 저장됨 (auto-commit), 하지만 리스너는 안 불림
        assertThat(orderRepository.findAll()).isNotEmpty();
    }

    /**
     * 함정 2: @EnableAsync 없이 @Async를 달면 동기로 실행된다.
     * Spring이 @Async를 처리하려면 @EnableAsync가 필요하다.
     * 없으면 @Async가 조용히 무시되어 호출자 스레드에서 동기로 실행된다.
     */
    @Test
    void EnableAsync_없이_Async를_달면_동기로_실행된다() {
        // SimpleAsyncTaskExecutor는 @EnableAsync의 기본 실행기다.
        // @EnableAsync가 없으면 @Async 어노테이션이 조용히 무시되어
        // 호출자 스레드에서 동기적으로 실행된다. 에러도 경고도 없다.
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        assertThat(executor.getThreadNamePrefix()).startsWith("SimpleAsyncTaskExecutor");
    }

    /**
     * 함정 3: AFTER_COMMIT에서 DB 저장은 REQUIRES_NEW로 새 TX를 열어야 안전하다.
     *
     * AFTER_COMMIT 시점에 기존 TX는 이미 커밋되었다.
     * Spring Data JPA의 repository.save()는 자체 @Transactional이 있어 우연히 동작하지만,
     * EntityManager를 직접 쓰거나 여러 DB 작업을 하나의 TX로 묶어야 할 때는
     * 반드시 REQUIRES_NEW로 명시적 TX를 열어야 한다.
     */
    @Test
    void AFTER_COMMIT_리스너에서_REQUIRES_NEW로_새_TX를_열면_DB_저장_가능() {
        // Given: REQUIRES_NEW 사용
        afterCommitDbSaveListener.enable();
        afterCommitDbSaveListener.setUseRequiresNew(true);

        // When
        orderService.createOrder("user-trap-new", 50_000L);

        // Then: 새 TX에서 저장 성공
        assertThat(afterCommitDbSaveListener.isExecuted()).isTrue();
        assertThat(pointRepository.findByUserId("user-trap-new")).isPresent();
    }

    /**
     * 함정 4: @Async 기본 스레드풀(SimpleAsyncTaskExecutor)은 스레드를 풀링하지 않는다.
     * 요청마다 새 스레드를 만들고, 프로덕션에서 OOM을 유발할 수 있다.
     */
    @Test
    void Async_기본_스레드풀은_스레드를_무한_생성하고_풀링하지_않는다() {
        // SimpleAsyncTaskExecutor: 매번 새 스레드 생성, 풀링 없음
        SimpleAsyncTaskExecutor simple = new SimpleAsyncTaskExecutor();

        // ThreadPoolTaskExecutor: 스레드 풀, 큐, 재사용
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(5);
        pool.setMaxPoolSize(10);
        pool.setQueueCapacity(100);
        pool.initialize();

        try {
            // ThreadPoolTaskExecutor는 명시적인 풀 사이즈가 있다
            assertThat(pool.getCorePoolSize()).isEqualTo(5);
            assertThat(pool.getMaxPoolSize()).isEqualTo(10);

            // 프로덕션에서는 반드시 ThreadPoolTaskExecutor를 사용해야 한다
        } finally {
            pool.shutdown();
        }
    }
}
