# Step 6 - Idempotent Consumer & Failure Isolation

> 메시지가 몇 번 오든 결과는 딱 한 번만 반영되어야 하고,
> 처리 불가능한 메시지는 격리되어야 한다.

---

## 학습 목표

- At Least Once 환경에서 중복 소비가 발생하는 상황을 체험한다
- 멱등 처리 패턴을 구현하고, 각 패턴의 트레이드오프를 이해한다
- 멱등 처리조차 실패하는 메시지를 DLQ로 격리하는 개념을 이해한다

---

## 시퀀스 다이어그램

### 중복 소비가 발생하는 이유

```mermaid
sequenceDiagram
    participant Kafka
    participant Consumer
    participant DB

    Kafka->>Consumer: 1. 메시지 전달 (offset 5)
    Consumer->>DB: 2. 포인트 적립 (성공)
    Note over Consumer: 3. offset commit 직전에 죽음!

    Consumer->>Consumer: 4. Consumer 재시작
    Kafka->>Consumer: 5. 같은 메시지 재전달 (offset 5)
    Consumer->>DB: 6. 포인트 또 적립 (중복!)
```

### 패턴 1: event_handled 테이블 (범용)

```mermaid
sequenceDiagram
    participant Consumer
    participant EH as event_handled
    participant DB

    Consumer->>EH: event_id 존재 확인
    EH-->>Consumer: 없음
    Consumer->>DB: 비즈니스 로직 실행
    Consumer->>EH: event_id 기록

    Note over Consumer,DB: --- 같은 메시지 재전달 ---

    Consumer->>EH: event_id 존재 확인
    EH-->>Consumer: 이미 있음
    Consumer-->>Consumer: SKIP
```

### 패턴 2: Upsert (집계성 데이터)

```mermaid
sequenceDiagram
    participant Consumer
    participant DB

    Note over Consumer: "상품 1001 조회수 = 150"
    Consumer->>DB: UPSERT (productId=1001, count=150)
    Note over DB: 최종 상태를 덮어쓰므로<br/>몇 번 실행해도 결과 동일
```

### 패턴 3: version 비교 (순서 보호)

```mermaid
sequenceDiagram
    participant Consumer
    participant DB

    Note over Consumer: v3: "재고 = 50"
    Consumer->>DB: 현재 version = 2
    Consumer->>DB: UPDATE stock=50, version=3

    Note over Consumer: v2: "재고 = 80" (지연 도착)
    Consumer->>DB: 현재 version = 3
    Consumer-->>Consumer: v2 < v3 -> SKIP
```

### DLQ (Dead Letter Queue)

```mermaid
sequenceDiagram
    participant Kafka as Source Topic
    participant Consumer
    participant DLQ as DLQ Topic

    Kafka->>Consumer: poison pill 메시지
    Consumer->>Consumer: 파싱 실패!
    Consumer->>DLQ: 격리 (DLQ로 이동)

    Kafka->>Consumer: 다음 정상 메시지
    Consumer->>Consumer: 정상 처리

    Note over DLQ: 운영자가 DLQ 메시지를<br/>수동 확인/재처리
```

---

## 멱등 패턴 비교

| 패턴 | 적합한 상황 | 트레이드오프 |
|------|-----------|------------|
| event_handled(event_id PK) | 범용, 어떤 도메인이든 적용 가능 | 별도 테이블 필요, 조회 비용 |
| Upsert | 집계성 데이터 (조회수, 좋아요수) | 도메인 특성에 의존, 범용성 낮음 |
| version / updated_at 비교 | 순서 역전까지 방어해야 하는 경우 | 구현 복잡도 높음 |

---

## 테스트 목록

### 전반부: 멱등 처리

| 테스트 클래스 | 메서드 | 증명하는 것 |
|---|---|---|
| DuplicateConsumptionProblemTest | 같은_메시지를_2번_소비하면_포인트가_2번_적립된다 | 문제 체험 |
| EventHandledIdempotencyTest | event_handled_테이블에_이미_처리된_이벤트가_있으면_스킵한다 | 패턴 1 검증 |
| EventHandledIdempotencyTest | 서로_다른_event_id의_메시지는_각각_정상_처리된다 | 정상 흐름 |
| UpsertIdempotencyTest | 같은_이벤트를_2번_처리해도_upsert로_올바른_결과가_유지된다 | 패턴 2 검증 |
| UpsertIdempotencyTest | upsert는_최신_값으로_덮어쓰므로_최종_상태가_보장된다 | 덮어쓰기 |
| UpsertIdempotencyTest | 다른_상품의_이벤트는_각각_독립적으로_upsert된다 | 독립성 |
| VersionComparisonIdempotencyTest | version이_현재보다_높은_이벤트만_반영된다 | 패턴 3 검증 |
| VersionComparisonIdempotencyTest | version이_현재보다_낮거나_같은_이벤트는_무시된다 | 역전 방어 |
| VersionComparisonIdempotencyTest | 순서가_역전된_이벤트_시퀀스에서_최종_상태가_올바르다 | 종합 검증 |

### 후반부: 실패 격리

| 테스트 클래스 | 메서드 | 증명하는 것 |
|---|---|---|
| PoisonPillAndDlqTest | 파싱_불가능한_메시지가_Consumer를_막는다 | 문제 체험 |
| PoisonPillAndDlqTest | 처리_실패한_메시지를_DLQ_토픽으로_격리할_수_있다 | DLQ 격리 |

## 이 Step이 도구에 종속되지 않는 이유

멱등 처리와 실패 격리는 Kafka든 RabbitMQ든 Redis Streams든 동일하게 필요한 패턴이다.
**"발행은 At Least Once, 소비는 멱등하게, 실패는 격리"** — 이것이 신뢰 가능한 이벤트 파이프라인의 최종 공식이다.
