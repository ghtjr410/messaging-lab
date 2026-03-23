# messaging-lab

> Learn why each messaging tool exists by hitting the limits of the previous one.
> From in-process events to Kafka, experience the tradeoffs that drive the evolution of event delivery.

---

## 이 lab의 역할

각 도구의 **"존재 이유"**를 체험하는 lab입니다.
동작 원리는 "다음 step이 왜 필요한지 납득할 수 있는 최소한"만 다룹니다.
특정 도구의 설정, 튜닝, 운영 전략은 다루지 않습니다. (→ kafka-lab)

### 학습 구조

"잘 되는 것"보다 **"안 되는 것"을 먼저 확인**합니다.
각 Step은 이전 Step의 한계를 직접 체험한 뒤, 그 한계를 해결하는 다음 도구로 넘어갑니다.

```
Step 0  "Event와 Command는 다르다"
Step 1  "직접 호출하면 결합도가 높아진다"            → 이벤트로 끊자
Step 2  "이벤트가 트랜잭션을 모르면 위험하다"        → 커밋 후에만 실행하자
        ⚡ 이 순간 Eventual Consistency를 수용한 것
Step 3  "메모리 이벤트는 서버 죽으면 사라진다"       → DB에 저장하자
        ── 여기까지: 유실 방지 축 ──
        ── 여기서부터: 프로세스 간 전달 축 ──
Step 4  "단일 프로세스 안에서만 전달된다"            → 프로세스 밖으로 보내자 (비보존)
Step 5  "보내긴 했는데 저장이 안 돼서 재처리 불가"    → 로그로 보존하자
        🔗 Step 3의 Event Store + Kafka Relay = Transactional Outbox 완성
Step 6  "재처리 가능하니까 중복이 온다"              → 멱등하게 처리하고, 실패는 격리하자
```

---

## Step 0 — Command vs Event

> 같은 메시지 인프라를 쓰더라도, 안에 담기는 것의 **의미**가 다르면 Consumer의 책임이 완전히 달라진다.

### 학습 목표

- Command와 Event의 본질적 차이를 구분한다
- 같은 인프라라도 담기는 내용에 따라 설계가 달라짐을 이해한다

### 확인할 것

- Command: "쿠폰을 발급해라" → 아직 일어나지 않은 일, 실패할 수 있음, 1:1 지시
- Event: "주문이 생성되었다" → 이미 확정된 사실, 발행자는 누가 듣는지 모름, 1:N 통지
- 같은 주문 도메인에서 Command와 Event를 각각 식별해보기

### 왜 먼저 다루는가

이걸 구분하지 않으면 Step 5에서 토픽을 설계할 때
`coupon-issue-requests`(Command)와 `order-events`(Event)를 같은 성격으로 취급하게 된다.

> 📌 이 구분은 이후 Step에서 계속 돌아옵니다.
> Step 3: "DB에 저장하는 건 Event인가 Command인가?"
> Step 5: "토픽 설계 시 Command 토픽과 Event 토픽을 왜 분리하는가?"

---

## Step 1 — Application Event

> 직접 호출을 이벤트로 끊으면, 발행자는 누가 듣는지 몰라도 된다.

### 학습 목표

- 직접 호출 방식의 결합도 문제를 체험한다
- ApplicationEventPublisher로 전환 후 의존성이 제거되는 걸 확인한다

### 확인할 것

- 직접 호출: OrderService가 StockService, CouponService, PointService를 모두 알고 있음
- 후속 로직 추가할 때마다 OrderService 의존성이 늘어나는 걸 확인
- ApplicationEvent 전환 후 OrderService의 의존성이 줄어드는 걸 확인
- @EventListener 내부 예외가 발행자 트랜잭션을 롤백시키는 걸 확인

### 체험할 한계 → Step 2로

리스너에서 예외가 발생하면 주문 트랜잭션까지 롤백된다.
포인트 적립 실패 때문에 주문이 취소되는 건 우리가 원한 게 아니다.

---

## Step 2 — Transactional Event + Eventual Consistency

> 트랜잭션이 확정된 이후에만 후속 처리를 실행해야 안전하다.
> 단, 이 선택을 한 순간 "즉시 일관성"은 포기한 것이다.

### 학습 목표

- 트랜잭션 커밋 타이밍과 이벤트 실행 타이밍의 관계를 이해한다
- @Async의 편리함과 실패 은닉 문제를 동시에 체험한다
- **Eventual Consistency를 수용한 것임을 인식한다**

### 확인할 것

- @EventListener → 커밋 전에 실행돼서 롤백 시 꼬이는 시나리오 재현
- @TransactionalEventListener(phase = AFTER_COMMIT) 전환 후 안전해지는 걸 확인
- @Async 추가 → API 응답 속도 개선 확인
- @Async 리스너 내부 예외가 호출자에게 전파되지 않는 걸 확인 (실패가 보이지 않음)
- 주문 직후 포인트 조회 시 아직 반영 안 된 상태를 확인 (일시적 불일치)

### ⚡ 이 Step에서 인식해야 할 것

```
AFTER_COMMIT + @Async를 선택한 이 순간,
우리는 Eventual Consistency를 수용한 것입니다.

주문은 즉시 확정되지만, 포인트 적립은 "곧" 반영됩니다.
이 "곧"이 얼마나 걸릴지는 시스템 상황에 따라 다릅니다.
조회 시 일시적 불일치가 발생할 수 있으며, 이건 버그가 아니라 설계 결정입니다.
```

### 체험할 한계 → Step 3으로

@Async로 별도 스레드에서 도는 순간, 서버가 재시작되면 메모리의 이벤트는 증발한다.
처리 중이던 포인트 적립은 영원히 실행되지 않는다.

---

## Step 3 — Event Store (이벤트를 DB에 안전하게 기록하기)

> 이벤트도 데이터다. DB에 같이 저장하면 서버가 죽어도 이벤트는 살아남는다.

### 학습 목표

- 메모리 이벤트의 유실 문제를 체험하고, DB 저장으로 해결한다
- 도메인 데이터와 이벤트를 하나의 트랜잭션으로 묶는 패턴을 구현한다

### 확인할 것

- @Async 이벤트 처리 중 서버 강제 종료 → 이벤트 유실 시나리오 재현
- Event Store 테이블 설계 (event_id, event_type, payload, status, created_at)
- 도메인 저장 + 이벤트 기록을 하나의 트랜잭션으로 묶기
- 스케줄러(릴레이)가 PENDING 이벤트를 읽어서 처리 후 상태 업데이트
- 서버 재시작 후에도 PENDING 이벤트가 남아있어서 재처리 가능한 걸 확인

> 📌 Step 0 콜백: 여기서 DB에 저장되는 건 Event(확정된 사실)다.
> "쿠폰을 발급해라"같은 Command를 이 패턴으로 저장하는 것이 적절한가? 생각해보자.

### 이 Step은 아직 완성형이 아니다

이 Step은 "이벤트를 DB에 안전하게 기록하는 것"까지만 다룹니다.
같은 서버의 스케줄러가 처리하므로 단일 프로세스 한계가 여전합니다.
**Step 5에서 이 Event Store를 Kafka로 릴레이하면, 그것이 실무에서 말하는 Transactional Outbox Pattern의 완성형입니다.**

### 체험할 한계 → Step 4로

Event Store를 같은 서버의 스케줄러가 처리하면, 단일 프로세스에 부하가 집중된다.
다른 시스템(정산, 알림, 분석)에도 이벤트를 보내야 한다면? 각각 폴링할 수는 없다.

---

## ── 관점 전환 ──

> Step 1~3은 **"이벤트를 잃지 않는 법"** (유실 방지 축)이었습니다.
> Step 4~5는 **"이벤트를 프로세스 밖으로 보내는 법"** (전달 범위 축)입니다.
>
> Step 4는 "보내긴 하는데 보존은 안 되는" 도구를 먼저 체험합니다.
> Step 5에서 "보내면서 보존도 되는" 도구로 넘어가며, 두 축을 동시에 해결합니다.

---

## Step 4 — Redis Pub/Sub (프로세스 밖 전달, 비보존)

> 프로세스 경계를 넘어 이벤트를 전달할 수 있다. 단, 듣고 있는 놈만 받는다.

### 학습 목표

- 이벤트가 프로세스 밖으로 나가면 다른 서버도 받을 수 있음을 확인한다
- 메시지 비보존의 의미와 적합한 사용처를 이해한다

### 확인할 것

- Redis Pub/Sub으로 서로 다른 애플리케이션 간 이벤트 전달
- 구독자가 없을 때 메시지 발행 → 유실되는 걸 확인
- 구독자가 죽었다 살아났을 때, 그 사이 메시지를 못 받는 걸 확인
- 적합한 케이스 확인: 캐시 무효화 신호, 실시간 알림 브로드캐스트

### 이 도구의 포지션

Redis Pub/Sub은 **"신뢰성이 필요 없는 실시간 브로드캐스트 전용"**입니다.
유실돼도 비즈니스에 구멍이 나지 않는 신호 전달에 적합합니다.
주문 이벤트, 결제 처리 같은 유실 불가 데이터에는 부적합합니다.

### 체험할 한계 → Step 5로

메시지가 저장되지 않는다. 구독자가 없으면 증발한다.
"어제 이벤트를 다시 처리해야 해"라는 요구가 오면 불가능하다.

---

## Step 5 — Kafka (프로세스 밖 전달, 보존, Outbox 완성)

> 메시지가 로그로 보존된다. 여러 Consumer가 각자 속도로 독립적으로 읽는다.
> Step 3의 Event Store를 Kafka로 릴레이하면 — Transactional Outbox Pattern이 완성된다.

### 학습 목표

- Kafka의 로그 기반 보존 모델을 체험한다
- 다중 Consumer Group의 독립적 소비를 확인한다
- **Step 3의 Event Store + Kafka Relay = Transactional Outbox Pattern 완성**

### 확인할 것

- Kafka Producer/Consumer 기본 파이프라인 구성
- 메시지 발행 후 Consumer 중지 → 재시작 후 이어서 읽는 걸 확인
- Consumer Group 2개가 같은 토픽을 독립적으로 소비하는 걸 확인
- Redis Pub/Sub과 비교: 구독자 없어도 메시지가 보존되는 걸 확인
- 같은 key의 메시지가 같은 파티션으로 가서 순서가 보장되는 걸 확인 (원리는 kafka-lab에서)
- Step 3의 Event Store 테이블을 Kafka로 릴레이하는 구조 구현

> 📌 Step 0 콜백: 토픽 설계 시 Command와 Event를 분리한다.
> `coupon-issue-requests` (Command) vs `order-events` (Event) — 왜 분리하는가?

### 🔗 Transactional Outbox Pattern 완성

```
Step 3에서 한 것:  도메인 저장 + Event Store 기록 (같은 TX)
이 Step에서 추가:  릴레이가 Event Store → Kafka로 발행
합치면:           Transactional Outbox Pattern
```

### 중복이 왜 발생하는가 (Step 6으로의 연결)

Kafka는 At Least Once 전달이 기본이다.
Consumer가 메시지를 처리한 뒤 offset을 커밋하기 직전에 죽으면,
재시작 시 같은 메시지를 다시 읽게 된다.
이 메커니즘 때문에 중복 소비가 발생한다.

> offset 관리의 세부 전략(auto-commit vs manual commit, commitSync vs commitAsync)은 kafka-lab에서 다룹니다.

### 체험할 한계 → Step 6으로

같은 메시지가 2번 오면 포인트가 2번 적립되거나 쿠폰이 2번 발급된다.
그리고 파싱 불가능한 메시지가 들어오면 Consumer가 무한 실패 루프에 빠진다.

---

## Step 6 — Idempotent Consumer & Failure Isolation

> 메시지가 몇 번 오든 결과는 딱 한 번만 반영되어야 하고,
> 처리 불가능한 메시지는 격리되어야 한다.

### 학습 목표

- At Least Once 환경에서 중복 소비가 발생하는 상황을 체험한다
- 멱등 처리 패턴을 구현하고, 각 패턴의 트레이드오프를 이해한다
- 멱등 처리조차 실패하는 메시지를 DLQ로 격리하는 개념을 이해한다

### 전반부: 멱등 처리 (성공적인 중복 방어)

- 같은 메시지를 2번 소비했을 때 데이터가 2번 반영되는 시나리오 재현
- 세 가지 멱등 패턴 구현 및 트레이드오프 비교

| 패턴 | 적합한 상황 | 트레이드오프 |
| --- | --- | --- |
| event_handled(event_id PK) | 범용, 어떤 도메인이든 적용 가능 | 별도 테이블 필요, 조회 비용 |
| Upsert | 집계성 데이터 (조회수, 좋아요수, 판매량) | 도메인 특성에 의존, 범용성 낮음 |
| version / updated_at 비교 | 순서 역전까지 방어해야 하는 경우 | 구현 복잡도 높음 |

### 후반부: 실패 격리 (처리 불가능한 메시지 방어)

- 파싱 불가능한 메시지(poison pill) 발행 → Consumer 무한 실패 루프 재현
- "N회 재시도 후 DLQ로 격리"라는 개념 확인
- DLQ에 격리된 메시지를 확인하는 시나리오

> DLQ 토픽 설계, retry backoff 전략, Spring Kafka의 ErrorHandler 설정 등 세부 구현은 kafka-lab에서 다룹니다.

### 이 Step이 도구에 종속되지 않는 이유

멱등 처리와 실패 격리는 Kafka든 RabbitMQ든 Redis Streams든 동일하게 필요한 패턴이다.
"발행은 At Least Once, 소비는 멱등하게, 실패는 격리" — 이것이 신뢰 가능한 이벤트 파이프라인의 최종 공식이다.

> 📌 Kafka에는 Exactly-Once Semantics(EOS)가 존재하지만, 이 lab에서는 다루지 않습니다.
> EOS는 Kafka 내부의 produce-consume 사이클에서만 보장되며,
> consumer 측 비즈니스 로직의 멱등성을 대체하지 못합니다.
> 상세한 EOS 동작과 한계는 kafka-lab에서 별도로 다룹니다.

---

## 이 lab이 다루지 않는 것

| 주제 | 다루는 곳 |
| --- | --- |
| Kafka 설정 깊이 파기 (acks, commit 전략, rebalancing) | kafka-lab |
| 파티션 수 vs Consumer 수 역학, 리밸런싱 동작 | kafka-lab |
| DLQ 토픽 설계, retry backoff 전략, ErrorHandler 구성 | kafka-lab |
| Exactly-Once Semantics 동작과 한계 | kafka-lab |
| Consumer lag 모니터링, 운영 지표 | kafka-lab |
| CDC (Debezium) 기반 Outbox 릴레이 | 별도 주제 |
| 보상 트랜잭션 설계 | 별도 주제 |