# Step 2 - Transactional Event + Eventual Consistency

> 트랜잭션이 확정된 이후에만 후속 처리를 실행해야 안전하다.
> 단, 이 선택을 한 순간 "즉시 일관성"은 포기한 것이다.

---

## 학습 목표

- 트랜잭션 커밋 타이밍과 이벤트 실행 타이밍의 관계를 이해한다
- @Async의 편리함과 실패 은닉 문제를 동시에 체험한다
- **Eventual Consistency를 수용한 것임을 인식한다**

---

## 시퀀스 다이어그램

### 위험: @EventListener는 커밋 전에 실행된다

```mermaid
sequenceDiagram
    participant OrderService
    participant DB
    participant PointListener

    Note over OrderService: TX BEGIN

    OrderService->>DB: INSERT 주문
    OrderService->>PointListener: @EventListener -> 동기 실행

    alt 리스너 실행 후 트랜잭션 롤백
        PointListener->>PointListener: 외부 API 호출 (이미 실행됨)
        Note over DB: TX ROLLBACK!
        Note over PointListener: 외부 API 호출은<br/>되돌릴 수 없다
    end
```

### 안전: @TransactionalEventListener(AFTER_COMMIT)

```mermaid
sequenceDiagram
    participant OrderService
    participant DB
    participant PointListener

    Note over OrderService: TX BEGIN

    OrderService->>DB: INSERT 주문
    OrderService->>OrderService: publish(OrderCreatedEvent)
    Note right of OrderService: 이벤트는 "등록"만 됨<br/>아직 실행되지 않음

    Note over OrderService: TX COMMIT

    Note over PointListener: 커밋 확인 후 실행됨
    PointListener->>DB: INSERT 포인트 (새 TX)

    Note over OrderService,PointListener: 주문은 이미 확정됨<br/>포인트 실패해도 주문은 안전
```

### @Async: 빠르지만 실패가 보이지 않는다

```mermaid
sequenceDiagram
    participant Client
    participant OrderService
    participant DB
    participant AsyncListener

    Client->>OrderService: 주문 생성 요청
    Note over OrderService: TX BEGIN
    OrderService->>DB: INSERT 주문
    Note over OrderService: TX COMMIT

    OrderService-->>Client: 200 OK (빠른 응답!)
    Note right of Client: 응답 시점에 포인트는<br/>아직 처리 안 됨

    Note over AsyncListener: 별도 스레드에서 실행
    AsyncListener->>DB: INSERT 포인트

    alt 포인트 적립 실패
        AsyncListener--xAsyncListener: RuntimeException!
        Note over AsyncListener: 예외가 호출자에게<br/>전파되지 않는다!
        Note over Client: Client는 성공으로 알고 있다
    end
```

### Eventual Consistency

```mermaid
sequenceDiagram
    participant Client
    participant OrderService
    participant AsyncListener
    participant DB

    Client->>OrderService: 주문 생성
    OrderService->>DB: 주문 저장 + COMMIT

    OrderService-->>Client: 200 OK

    Client->>DB: 포인트 조회
    DB-->>Client: 포인트 = 0 (아직 반영 안 됨!)

    Note over AsyncListener: 비동기 처리 중...
    AsyncListener->>DB: 포인트 적립

    Client->>DB: 포인트 조회 (잠시 후)
    DB-->>Client: 포인트 = 500 (이제 반영됨)

    Note over Client,DB: 이 "잠시"가 Eventual Consistency<br/>버그가 아니라 설계 결정이다
```

---

## 테스트 목록

| 테스트 클래스 | 메서드 | 증명하는 것 |
|---|---|---|
| EventListenerTimingTest | EventListener는_커밋_전에_실행되어_롤백시_부수효과가_되돌려지지_않는다 | 타이밍 위험 |
| TransactionalEventListenerTest | TransactionalEventListener는_커밋_후에만_실행된다 | 안전한 타이밍 |
| TransactionalEventListenerTest | 트랜잭션이_롤백되면_TransactionalEventListener는_실행되지_않는다 | 롤백 안전 |
| TransactionalEventListenerTest | TransactionalEventListener_예외는_발행자_트랜잭션에_영향을_주지_않는다 | 주문 TX 보호 |
| AsyncEventTest | Async_리스너는_별도_스레드에서_실행되어_응답이_빠르다 | 비동기 응답 |
| AsyncEventTest | Async_리스너_예외는_호출자에게_전파되지_않는다_실패가_숨겨진다 | 실패 은닉 |
| EventualConsistencyTest | 주문_직후_포인트를_조회하면_아직_반영되지_않았을_수_있다 | Eventual Consistency |
| AsyncEventLossTest | 서버가_재시작되면_Async_리스너가_처리하지_못한_이벤트는_유실된다 | **핵심 한계: 메모리 휘발** |

## 이 Step에서 인식해야 할 것

AFTER_COMMIT + @Async를 선택한 이 순간, Eventual Consistency를 수용한 것이다.

## 체험할 한계 -> Step 3으로

@Async로 별도 스레드에서 도는 순간, 서버가 재시작되면 메모리의 이벤트는 증발한다.
