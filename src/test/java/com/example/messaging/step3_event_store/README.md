# Step 3 - Event Store

> 이벤트도 데이터다. DB에 같이 저장하면 서버가 죽어도 이벤트는 살아남는다.

---

## 이 Step에서 해결하는 문제

Step 2에서 `@Async + @TransactionalEventListener(AFTER_COMMIT)`를 사용했습니다.
서버가 살아있으면 잘 동작하지만, **비동기 처리 도중 서버가 죽으면 메모리의 이벤트는 증발합니다.**

## 해결 방법

도메인 데이터를 저장할 때 이벤트도 **같은 트랜잭션**으로 DB에 기록합니다.
스케줄러(릴레이)가 PENDING 이벤트를 주기적으로 읽어서 처리합니다.

---

## 시퀀스 다이어그램

### 문제: 비동기 이벤트 유실

```mermaid
sequenceDiagram
    participant Client
    participant OrderService
    participant DB
    participant AsyncThread as @Async 스레드

    Client->>OrderService: 주문 생성 요청
    OrderService->>DB: 주문 저장 (COMMIT)
    OrderService-->>Client: 200 OK
    OrderService->>AsyncThread: 이벤트 전달

    Note over AsyncThread: 포인트 적립 시작...
    Note over AsyncThread: 이 순간 서버 강제 종료!
    Note over AsyncThread: 포인트 적립 영원히 실행 안 됨
```

### 해결: Event Store 기록

```mermaid
sequenceDiagram
    participant Client
    participant OrderService
    participant DB
    participant EventStore as event_records 테이블

    Client->>OrderService: 주문 생성 요청

    Note over OrderService: TX BEGIN
    OrderService->>DB: 주문 저장
    OrderService->>EventStore: 이벤트 기록 (status=PENDING)
    Note over OrderService: TX COMMIT

    OrderService-->>Client: 200 OK

    Note over EventStore: 서버가 죽어도<br/>PENDING 레코드는 DB에 남아있다
```

### 스케줄러 릴레이

```mermaid
sequenceDiagram
    participant Scheduler as 스케줄러 (릴레이)
    participant EventStore as event_records
    participant PointService

    loop 주기적 실행
        Scheduler->>EventStore: SELECT WHERE status = 'PENDING'
        EventStore-->>Scheduler: PENDING 이벤트 목록

        alt 이벤트 존재
            Scheduler->>PointService: 포인트 적립
            Scheduler->>EventStore: UPDATE status = 'PROCESSED'
        end
    end
```

### Event Store 테이블 구조

```mermaid
erDiagram
    event_records {
        Long id PK
        String eventType "ORDER_CREATED"
        String payload "JSON"
        String status "PENDING / PROCESSED"
        DateTime createdAt
    }

    orders ||--o{ event_records : "같은 TX로 기록"
```

---

## 테스트 목록

| 테스트 클래스 | 메서드 | 증명하는 것 |
|---|---|---|
| EventStoreAtomicityTest | 주문_저장과_이벤트_기록은_하나의_트랜잭션으로_묶인다 | 원자성 (정상) |
| EventStoreAtomicityTest | 주문_저장이_실패하면_이벤트_기록도_함께_롤백된다 | 원자성 (실패) |
| EventRelayTest | 스케줄러는_PENDING_상태의_이벤트를_조회하여_처리한다 | 릴레이 동작 |
| EventRelayTest | 처리_완료된_이벤트는_PROCESSED_상태로_변경된다 | 상태 전이 |
| EventRelayTest | 이미_처리된_이벤트는_다시_처리하지_않는다 | 중복 방지 |
| EventStoreRecoveryTest | 서버_재시작_후에도_PENDING_이벤트는_DB에_남아있다 | 내구성 |
| EventStoreRecoveryTest | 재시작_후_스케줄러가_PENDING_이벤트를_재처리한다 | 복구 가능성 |

## 이 Step은 아직 완성형이 아니다

Event Store를 같은 서버의 스케줄러가 처리하므로 **단일 프로세스 한계가 여전합니다.**
다른 시스템(정산, 알림, 분석)에도 이벤트를 보내야 한다면?

**Step 5에서 이 Event Store를 Kafka로 릴레이하면, Transactional Outbox Pattern이 완성됩니다.**

## 체험할 한계 -> Step 4로

단일 프로세스 안에서만 이벤트가 순환한다.
다른 서비스에 이벤트를 전달하려면 프로세스 경계를 넘어야 한다.
