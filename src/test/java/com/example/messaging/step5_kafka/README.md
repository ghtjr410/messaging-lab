# Step 5 - Kafka

> 메시지가 로그로 보존된다. 여러 Consumer가 각자 속도로 독립적으로 읽는다.
> Step 3의 Event Store를 Kafka로 릴레이하면 - Transactional Outbox Pattern이 완성된다.

---

## 핵심 개념

### Kafka 로그 모델

메시지는 토픽의 파티션에 **append-only 로그**로 기록된다.
Consumer는 각자의 offset을 관리하며, 같은 메시지를 여러 Consumer Group이 독립적으로 읽을 수 있다.

### Redis Pub/Sub vs Kafka

| 특성 | Redis Pub/Sub | Kafka |
|------|:---:|:---:|
| 메시지 보존 | X (즉시 폐기) | O (로그에 보존) |
| 구독자 없을 때 | 유실 | 보존 |
| Consumer 재시작 | 놓친 메시지 없음 | offset부터 이어 읽기 |
| 다중 Consumer | 브로드캐스트 | Consumer Group별 독립 소비 |

### Fan-Out vs Competing Consumers

Kafka는 두 가지 메시징 패턴을 모두 지원한다:

- **Fan-Out**: 서로 다른 Consumer Group이 같은 메시지를 독립적으로 소비 (정산 Group + 알림 Group)
- **Competing Consumers**: 같은 Consumer Group 내 여러 인스턴스가 파티션을 나눠 부하 분산

Redis Pub/Sub은 Fan-Out만 가능했지만, Kafka는 Consumer Group 개념으로 두 패턴을 동시에 지원한다.

---

## 시퀀스 다이어그램

### Consumer Group 독립성

```mermaid
sequenceDiagram
    participant Producer
    participant Kafka as Kafka (Topic: order-events)
    participant GroupA as Consumer Group A (정산)
    participant GroupB as Consumer Group B (알림)

    Producer->>Kafka: msg-1, msg-2, msg-3, msg-4, msg-5

    GroupA->>Kafka: poll (offset 0~2)
    Kafka-->>GroupA: msg-1, msg-2, msg-3
    Note over GroupA: 3건 처리 후 commit (offset=3)

    GroupB->>Kafka: poll (offset 0~4)
    Kafka-->>GroupB: msg-1, msg-2, msg-3, msg-4, msg-5
    Note over GroupB: 5건 전부 처리

    Note over GroupA: 재시작 후 offset 3부터 이어 읽기
    GroupA->>Kafka: poll (offset 3~)
    Kafka-->>GroupA: msg-4, msg-5

    Note over GroupA,GroupB: 각 Group은 독립적인 offset을 관리한다
```

### 같은 Key -> 같은 Partition -> 순서 보장

```mermaid
sequenceDiagram
    participant Producer
    participant Kafka

    Producer->>Kafka: key=order-1001, "생성"
    Producer->>Kafka: key=order-1002, "생성"
    Producer->>Kafka: key=order-1001, "결제"
    Producer->>Kafka: key=order-1001, "배송"

    Note over Kafka: Partition 0: [order-1002:생성]
    Note over Kafka: Partition 1: [order-1001:생성, 결제, 배송]
    Note over Kafka: 같은 key(1001)는 항상 같은 파티션<br/>-> 순서 보장
```

### Transactional Outbox Pattern 완성

```mermaid
sequenceDiagram
    participant App as Application
    participant DB as Database
    participant Outbox as outbox_events 테이블
    participant Relay as Relay (Scheduler)
    participant Kafka

    Note over App: TX BEGIN
    App->>DB: 1. 주문 저장
    App->>Outbox: 2. 이벤트 기록 (PENDING)
    Note over App: TX COMMIT

    Relay->>Outbox: 3. PENDING 이벤트 조회
    Relay->>Kafka: 4. Kafka로 발행
    Relay->>Outbox: 5. 상태를 SENT로 변경

    Kafka-->>Consumer1: Consumer Group A (정산)
    Kafka-->>Consumer2: Consumer Group B (알림)

    Note over DB,Kafka: Step 3 Event Store + Kafka Relay<br/>= Transactional Outbox Pattern
```

---

## 테스트 목록

| 테스트 클래스 | 메서드 | 증명하는 것 |
|---|---|---|
| KafkaBasicPipelineTest | Producer가_보낸_메시지를_Consumer가_수신한다 | 기본 파이프라인 |
| KafkaBasicPipelineTest | 여러_메시지를_순서대로_발행하면_같은_파티션에서_순서대로_소비된다 | 파티션 내 순서 |
| KafkaMessagePreservationTest | Consumer가_중지된_사이에_발행된_메시지를_재시작_후_이어서_읽는다 | 메시지 보존 |
| KafkaMessagePreservationTest | 구독자가_없어도_메시지는_Kafka에_보존된다 | Redis 대비 차이 |
| KafkaConsumerGroupIndependenceTest | 두_Consumer_Group이_같은_토픽의_모든_메시지를_각각_독립적으로_수신한다 | Group 독립성 |
| KafkaConsumerGroupIndependenceTest | 한_Consumer_Group의_소비_속도가_다른_Group에_영향을_주지_않는다 | 속도 독립성 |
| KafkaPartitionOrderingTest | 같은_key의_메시지는_같은_파티션에_저장된다 | Key-Partition 매핑 |
| KafkaPartitionOrderingTest | 같은_파티션의_메시지는_발행_순서대로_소비된다 | 파티션 내 순서 |
| KafkaPartitionOrderingTest | 다른_key의_메시지는_다른_파티션으로_분배될_수_있다 | 파티션 분배 |
| TransactionalOutboxCompletionTest | 주문_저장과_이벤트_기록이_하나의_트랜잭션으로_묶인다 | 원자성 |
| TransactionalOutboxCompletionTest | 릴레이가_PENDING_이벤트를_Kafka로_발행하고_SENT로_변경한다 | Outbox 완성 |
| TransactionalOutboxCompletionTest | Kafka_발행_실패_시_이벤트는_여전히_PENDING_상태를_유지한다 | 실패 복구 |

## Testcontainer

```
KafkaContainer("confluentinc/cp-kafka:7.6.0") - KRaft mode (ZooKeeper 불필요)
```

## 중복이 왜 발생하는가 -> Step 6

Kafka는 At Least Once 전달이 기본이다.
Consumer가 메시지를 처리한 뒤 offset을 커밋하기 직전에 죽으면,
재시작 시 같은 메시지를 다시 읽게 된다.
-> 포인트가 2번 적립되거나 쿠폰이 2번 발급된다.
