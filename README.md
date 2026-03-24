# messaging-lab

**이전 도구의 한계를 직접 체험한 뒤, 그 한계를 해결하는 다음 도구로 넘어가는 메시징 학습 테스트.**

> Learn why each messaging tool exists by hitting the limits of the previous one.
> From in-process events to Kafka, experience the tradeoffs that drive the evolution of event delivery.

---

## 목차

- [프로젝트 소개](#프로젝트-소개)
- [시작하기](#시작하기)
- [학습 구조](#학습-구조)
- [학습 순서 가이드](#학습-순서-가이드)
- [테스트 목록](#테스트-목록)
- [이 lab이 다루지 않는 것](#이-lab이-다루지-않는-것)
- [EDA Overview - 큰 그림 보기](EDA-OVERVIEW.md)
- [용어 정리 (Glossary)](GLOSSARY.md)

---

## 프로젝트 소개

각 도구의 **"존재 이유"**를 체험하는 lab입니다.
동작 원리는 "다음 step이 왜 필요한지 납득할 수 있는 최소한"만 다룹니다.
특정 도구의 설정, 튜닝, 운영 전략은 다루지 않습니다. (→ kafka-lab)

**"잘 되는 것"보다 "안 되는 것"을 먼저 확인합니다.**
각 Step은 이전 Step의 한계를 직접 체험한 뒤, 그 한계를 해결하는 다음 도구로 넘어갑니다.
모든 테스트 이름이 곧 증명 명제입니다.

> 전체 흐름을 먼저 조감하고 싶다면 [EDA Overview](EDA-OVERVIEW.md)를,
> Step 3 완료 후 현재 위치를 확인하고 싶다면 같은 문서의 포지션 맵을 참고하세요.

---

## 시작하기

### 기술 스택

- **Java 21** / **Spring Boot 3.4.3**
- **Spring Kafka** — Step 6, 7의 Producer/Consumer
- **Spring AMQP** — Step 5의 RabbitMQ
- **Spring Data Redis** — Step 4의 Pub/Sub
- **Spring Data JPA (H2)** — Step 3의 Event Store
- **Testcontainers** — Redis, RabbitMQ, Kafka 컨테이너 자동 기동

### 필요 환경

- **Java 21** 이상
- **Docker** 실행 중 (Testcontainers가 Redis, RabbitMQ, Kafka 컨테이너를 자동으로 띄웁니다)

### 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 Step만 실행
./gradlew test --tests "com.example.messaging.step1_application_event.*"

# 특정 테스트 클래스만 실행
./gradlew test --tests "CommandEventConceptTest"
```

---

## 학습 구조

```
Step 0  "Event와 Command는 다르다"
Step 1  "직접 호출하면 결합도가 높아진다"            → 이벤트로 끊자
        한계: 리스너 예외가 발행자 트랜잭션을 롤백시킨다
        → try-catch는 땜빵이다. 근본 원인은 같은 트랜잭션 안에서 실행되기 때문.
Step 2  "트랜잭션 커밋 후에만 실행하면 아예 영향을 줄 수 없다"
        이 순간 Eventual Consistency를 수용한 것
        함정: TX 없이 발행하면 리스너가 아예 안 불린다
        함정: @EnableAsync 없이 @Async 달면 조용히 동기 실행
        함정: AFTER_COMMIT에서 DB 저장하려면 별도 빈의 REQUIRES_NEW 필요
Step 3  "메모리 이벤트는 서버 죽으면 사라진다"       → DB에 저장하자
        ── 여기까지: 유실 방지 축 ──
        모놀리식이라면 여기까지로 충분할 수 있다.
        하지만 포인트 서비스를 별도 팀이 독립 배포하게 되면,
        ApplicationEvent는 프로세스 경계를 넘지 못한다.
        ── 여기서부터: 프로세스 간 전달 축 ──
Step 4  "단일 프로세스 안에서만 전달된다"            → 프로세스 밖으로 보내자 (비보존)
Step 5  "보내긴 했는데 저장이 안 돼서 재처리 불가"    → 큐에 저장하자 (소비 후 삭제)
Step 6  "소비하면 삭제돼서 과거 이벤트 재처리 불가"   → 로그로 보존하자 (소비해도 남음)
        Step 3의 Event Store + Kafka Relay = Transactional Outbox 완성
Step 7  "재처리 가능하니까 중복이 온다"              → 멱등하게 처리하고, 실패는 격리하자
```

처음이라면 반드시 **Step 0부터 순서대로** 진행하세요. 각 Step은 이전 Step의 한계를 전제로 합니다.

---

## 학습 순서 가이드

> 총 **71개 학습 테스트**. 각 테스트 이름이 곧 증명 명제입니다.

| Step | 주제 | 핵심 질문 | 테스트 | 인프라 |
|:----:|------|----------|:------:|:------:|
| 0 | **Command vs Event** | 같은 인프라인데 왜 설계가 달라지는가? | 7개 | 없음 |
| 1 | **Application Event** | 직접 호출 대신 이벤트를 쓰면 뭐가 좋은가? | 8개 | Spring Event |
| 2 | **Transactional Event** | 트랜잭션과 이벤트 타이밍은 왜 중요한가? | 16개 | Spring Event |
| 3 | **Event Store** | 서버가 죽으면 이벤트는 어디로 가는가? | 7개 | Spring Data JPA (H2) |
| 4 | **Redis Pub/Sub** | 프로세스 밖으로 이벤트를 보내면? | 4개 | Spring Data Redis |
| 5 | **RabbitMQ** | 큐에 저장하면 유실이 해결되는가? | 6개 | Spring AMQP |
| 6 | **Kafka** | 메시지를 보존하면서 전달하려면? | 12개 | Spring Kafka |
| 7 | **Idempotent Consumer** | 같은 메시지가 2번 오면 어떻게 되는가? | 11개 | Spring Kafka |

### Step 0 — Command vs Event

Command와 Event의 본질적 차이를 구분합니다.
이 구분은 Step 3(DB 저장 대상), Step 6(토픽 설계)에서 계속 돌아옵니다.

- Command: "쿠폰을 발급해라" → 아직 일어나지 않은 일, 실패할 수 있음, 1:1 지시
- Event: "주문이 생성되었다" → 이미 확정된 사실, 발행자는 누가 듣는지 모름, 1:N 통지
- 둘 다 타임스탬프가 있을 수 있지만 의미가 다르다: requestedAt(요청 시각) vs occurredAt(확정 시각)

### Step 1 — Application Event

직접 호출 방식의 결합도 문제를 체험하고, `ApplicationEventPublisher`로 전환 후 의존성이 제거되는 것을 확인합니다.

- 직접 호출: OrderService가 StockService, CouponService, PointService를 모두 알고 있음
- ApplicationEvent 전환 후 의존성 제거 확인
- **한계 발견:** `@EventListener` 내부 예외가 발행자 트랜잭션을 롤백시킨다

### Step 2 — Transactional Event + Eventual Consistency

`@TransactionalEventListener`의 4가지 phase를 비교하고, 왜 `AFTER_COMMIT`이 안전한지 증명합니다.
`@Async`로 응답 속도를 개선하지만, 거기에 따르는 함정과 한계를 체험합니다.

> **이 Step에서 인식해야 할 것:** AFTER_COMMIT + @Async를 선택한 순간,
> Eventual Consistency를 수용한 것입니다.

**[Phase 비교] — 왜 AFTER_COMMIT인가?**

- BEFORE_COMMIT: 커밋 전에 실행. 리스너 예외가 발행자 TX를 롤백시킨다 (Step 1과 같은 위험)
- AFTER_COMMIT: 커밋 후에 실행. 리스너 예외가 발행자 TX에 영향 없다 (안전)
- AFTER_ROLLBACK: 롤백 후에만 실행. 보상 로직/알림에 사용
- AFTER_COMPLETION: 커밋/롤백 무관하게 실행. 리소스 정리에 사용

**[함정] — 모르면 밟는 것들**

- 트랜잭션 없이 이벤트를 발행하면 `@TransactionalEventListener`가 아예 불리지 않는다
- `@EnableAsync` 없이 `@Async`를 달면 조용히 동기 실행된다
- AFTER_COMMIT 리스너에서 DB 저장하려면 별도 빈의 REQUIRES_NEW가 필요 (self-invocation 방지)
- `@Async` 기본 스레드풀 설정은 프로덕션에 부적합하다 (커스텀 설정 필요)

**[한계 발견]**

- `@Async` 리스너 예외는 호출자에게 전파되지 않는다 (실패 은닉)
- 서버가 재시작되면 메모리의 이벤트는 증발한다 → **Step 3으로**

### Step 3 — Event Store

도메인 저장과 이벤트 기록을 하나의 트랜잭션으로 묶어 유실을 방지합니다.
스케줄러(릴레이)가 PENDING 이벤트를 처리합니다.

- Event Store 테이블: event_id, event_type, payload, status, created_at
- 서버 재시작 후에도 PENDING 이벤트가 남아있어서 재처리 가능
- **Step 6에서 Kafka로 릴레이하면 Transactional Outbox Pattern 완성**

### Step 4 — Redis Pub/Sub (프로세스 밖 전달, 비보존)

이벤트가 프로세스 경계를 넘어 전달되는 것을 확인합니다.
구독자가 없으면 메시지는 증발합니다.

- 적합한 케이스: 캐시 무효화 신호, 실시간 알림 브로드캐스트
- **한계 발견:** 메시지가 저장되지 않아 재처리 불가

### Step 5 — RabbitMQ (큐에 저장, 소비 후 삭제)

메시지를 큐에 저장해서 구독자가 없어도 보존합니다. Redis Pub/Sub의 유실 문제를 해결합니다.
같은 큐에서 부하를 나누는 Competing Consumers 패턴을 체험합니다.

- Consumer가 없어도 메시지가 큐에 남아있음 (Redis 대비 차이)
- Competing Consumers: 같은 큐에서 메시지를 나눠 처리
- **한계 발견:** ACK하면 큐에서 삭제. "어제 이벤트를 다시 처리해야 해"가 불가능

### Step 6 — Kafka (프로세스 밖 전달, 보존, Outbox 완성)

메시지가 로그로 보존되어 소비해도 남아있습니다. 여러 Consumer Group이 독립적으로 소비합니다.

```
Step 3에서 한 것:  도메인 저장 + Event Store 기록 (같은 TX)
이 Step에서 추가:  릴레이가 Event Store → Kafka로 발행
합치면:           Transactional Outbox Pattern
```

- **한계 발견:** 같은 메시지가 2번 오면 포인트가 2번 적립된다

### Step 7 — Idempotent Consumer & Failure Isolation

At Least Once 환경에서 중복 소비를 방어하는 세 가지 멱등 패턴을 구현합니다.

| 패턴 | 적합한 상황 | 트레이드오프 |
|------|-----------|------------|
| event_handled(event_id PK) | 범용, 어떤 도메인이든 적용 가능 | 별도 테이블 필요, 조회 비용 |
| Upsert | 집계성 데이터 (조회수, 좋아요수) | 도메인 특성에 의존, 범용성 낮음 |
| version / updated_at 비교 | 순서 역전까지 방어해야 하는 경우 | 구현 복잡도 높음 |

처리 불가능한 메시지(poison pill)는 DLQ로 격리합니다.

---

## 테스트 목록

> 각 Step의 README에 상세 설명이 있습니다.

<details>
<summary><b>Step 0 — Command vs Event (7개)</b></summary>

| 테스트 | 증명하는 것 |
|--------|-----------|
| Command는 미래시제다 아직 일어나지 않은 일 | requestedAt(요청 시각)이 있다 |
| Event는 과거시제다 이미 확정된 사실 | occurredAt(확정 시각)이 있다 |
| Command는 수신자를 특정한다 1대1 | 발신자가 handler를 직접 호출 |
| Event는 수신자를 모른다 1대N | 리스너 수와 무관 |
| Command는 실패할 수 있고 발신자가 처리해야 한다 | 예외 발생 시 발신자 책임 |
| Event는 이미 일어난 사실이므로 발행 자체는 실패하지 않는다 | 발행은 항상 성공 |
| 같은 도메인에서 Command 실행 결과가 Event가 된다 | Command -> 실행 -> Event |

</details>

<details>
<summary><b>Step 1 — Application Event (8개)</b></summary>

| 테스트 | 증명하는 것 |
|--------|-----------|
| 직접 호출 방식에서 OrderService는 모든 후속 서비스에 의존한다 | 생성자 의존성 4개 |
| 직접 호출 방식에서 후속 처리 실패시 주문도 롤백된다 | 강한 결합의 부작용 |
| 직접 호출 방식에서 모든 후속 처리가 성공하면 주문이 완료된다 | 정상 흐름 |
| 이벤트 방식에서 OrderService는 EventPublisher에만 의존한다 | 의존성 2개로 감소 |
| 이벤트 발행 후 리스너가 정상 처리하면 모든 데이터가 저장된다 | 정상 흐름 |
| 후속 로직 추가시 OrderService는 수정하지 않아도 된다 | OCP 원칙 |
| 리스너 예외가 발행자 트랜잭션을 롤백시킨다 | **핵심 한계** |
| EventListener는 발행자와 같은 스레드에서 동기적으로 실행된다 | 동일 TX |

</details>

<details>
<summary><b>Step 2 — Transactional Event (16개)</b></summary>

**[Phase 비교]**

| 테스트 | 증명하는 것 |
|--------|-----------|
| BEFORE COMMIT 리스너에서 예외 발생 시 발행자 TX가 롤백된다 | **Step 1과 같은 위험** |
| TransactionalEventListener는 커밋 후에만 실행된다 | 안전한 타이밍 |
| 트랜잭션이 롤백되면 TransactionalEventListener는 실행되지 않는다 | 롤백 안전 |
| AFTER ROLLBACK 리스너는 롤백 후에만 실행된다 | 보상/알림 용도 |
| AFTER COMPLETION 리스너는 커밋 롤백 무관하게 실행된다 | 리소스 정리 용도 |

**[함정]**

| 테스트 | 증명하는 것 |
|--------|-----------|
| 트랜잭션 없이 이벤트를 발행하면 TransactionalEventListener가 불리지 않는다 | **@Transactional 빠뜨리면 리스너 무시** |
| EnableAsync가 있어야 Async 리스너가 별도 스레드에서 실행된다 | **@EnableAsync 없으면 동기 실행** |
| AFTER COMMIT 리스너에서 DB 저장하면 TransactionRequiredException 발생 | 기존 TX는 이미 커밋됨 |
| AFTER COMMIT 리스너에서 별도 빈의 REQUIRES NEW로 새 TX를 열면 DB 저장 가능 | 해결: 별도 빈 + 새 트랜잭션 |
| Async 기본 스레드풀은 제한 없이 설정되어 프로덕션에서 튜닝이 필요하다 | 기본 설정의 위험 |

**[한계 발견]**

| 테스트 | 증명하는 것 |
|--------|-----------|
| EventListener에서 외부 API를 호출한 뒤 트랜잭션이 롤백되면 호출은 되돌릴 수 없다 | 비트랜잭션 부수효과 위험 |
| TransactionalEventListener 예외는 발행자 트랜잭션에 영향을 주지 않는다 | TX 보호 |
| Async 리스너는 별도 스레드에서 실행되어 응답이 빠르다 | 비동기 응답 |
| Async 리스너 예외는 호출자에게 전파되지 않는다 실패가 숨겨진다 | **실패 은닉** |
| 주문 직후 포인트를 조회하면 아직 반영되지 않았을 수 있다 | Eventual Consistency |
| 서버가 재시작되면 Async 리스너가 처리하지 못한 이벤트는 유실된다 | **핵심 한계: 메모리 휘발** |

</details>

<details>
<summary><b>Step 3 — Event Store (7개)</b></summary>

| 테스트 | 증명하는 것 |
|--------|-----------|
| 주문 저장과 이벤트 기록은 하나의 트랜잭션으로 묶인다 | 원자성 (정상) |
| 주문 저장이 실패하면 이벤트 기록도 함께 롤백된다 | 원자성 (실패) |
| 스케줄러는 PENDING 상태의 이벤트를 조회하여 처리한다 | 릴레이 동작 |
| 처리 완료된 이벤트는 PROCESSED 상태로 변경된다 | 상태 전이 |
| 이미 처리된 이벤트는 다시 처리하지 않는다 | 중복 방지 |
| 서버 재시작 후에도 PENDING 이벤트는 DB에 남아있다 | 내구성 |
| 재시작 후 스케줄러가 PENDING 이벤트를 재처리한다 | 복구 가능성 |

</details>

<details>
<summary><b>Step 4 — Redis Pub/Sub (4개)</b></summary>

| 테스트 | 증명하는 것 |
|--------|-----------|
| 발행한 메시지를 구독자가 수신한다 | 기본 동작 |
| 여러 구독자가 동일한 메시지를 모두 수신한다 | 브로드캐스트 |
| 구독자가 없으면 발행된 메시지는 유실된다 | **비보존** |
| 구독자가 다운된 동안 발행된 메시지는 수신할 수 없다 | **다운타임 유실** |

</details>

<details>
<summary><b>Step 5 — RabbitMQ (6개)</b></summary>

| 테스트 | 증명하는 것 |
|--------|-----------|
| Producer가 보낸 메시지를 Consumer가 수신한다 | 기본 파이프라인 |
| Consumer가 없어도 메시지는 큐에 보존된다 | **Redis 대비: 보존** |
| Consumer가 다운된 동안 발행된 메시지를 재시작 후 수신한다 | 다운타임 보존 |
| 같은 큐의 Consumer 2개가 메시지를 나눠 처리한다 | Competing Consumers |
| ACK한 메시지는 큐에서 삭제되어 다시 읽을 수 없다 | **소비 후 삭제** |
| 소비 완료된 메시지를 다른 Consumer가 다시 읽을 수 없다 | **재처리 불가** |

</details>

<details>
<summary><b>Step 6 — Kafka (12개)</b></summary>

| 테스트 | 증명하는 것 |
|--------|-----------|
| Producer가 보낸 메시지를 Consumer가 수신한다 | 기본 파이프라인 |
| 여러 메시지를 순서대로 발행하면 같은 파티션에서 순서대로 소비된다 | 파티션 내 순서 |
| Consumer가 중지된 사이에 발행된 메시지를 재시작 후 이어서 읽는다 | 메시지 보존 |
| 구독자가 없어도 메시지는 Kafka에 보존된다 | Redis 대비 차이 |
| 두 Consumer Group이 같은 토픽의 모든 메시지를 각각 독립적으로 수신한다 | Group 독립성 |
| 한 Consumer Group의 소비 속도가 다른 Group에 영향을 주지 않는다 | 속도 독립성 |
| 같은 key의 메시지는 같은 파티션에 저장된다 | Key-Partition 매핑 |
| 같은 파티션의 메시지는 발행 순서대로 소비된다 | 파티션 내 순서 |
| 다른 key의 메시지는 다른 파티션으로 분배될 수 있다 | 파티션 분배 |
| 주문 저장과 이벤트 기록이 하나의 트랜잭션으로 묶인다 | Outbox 원자성 |
| 릴레이가 PENDING 이벤트를 Kafka로 발행하고 SENT로 변경한다 | **Outbox 완성** |
| Kafka 발행 실패 시 이벤트는 여전히 PENDING 상태를 유지한다 | 실패 복구 |

</details>

<details>
<summary><b>Step 7 — Idempotent Consumer (11개)</b></summary>

| 테스트 | 증명하는 것 |
|--------|-----------|
| 같은 메시지를 2번 소비하면 포인트가 2번 적립된다 | **문제 체험** |
| event_handled 테이블에 이미 처리된 이벤트가 있으면 스킵한다 | 패턴 1: event_handled |
| 서로 다른 event_id의 메시지는 각각 정상 처리된다 | 정상 흐름 |
| 같은 이벤트를 2번 처리해도 upsert로 올바른 결과가 유지된다 | 패턴 2: Upsert |
| upsert는 최신 값으로 덮어쓰므로 최종 상태가 보장된다 | 덮어쓰기 |
| 다른 상품의 이벤트는 각각 독립적으로 upsert된다 | 독립성 |
| version이 현재보다 높은 이벤트만 반영된다 | 패턴 3: version |
| version이 현재보다 낮거나 같은 이벤트는 무시된다 | 역전 방어 |
| 순서가 역전된 이벤트 시퀀스에서 최종 상태가 올바르다 | 종합 검증 |
| 파싱 불가능한 메시지가 Consumer를 막는다 | Poison pill |
| 처리 실패한 메시지를 DLQ 토픽으로 격리할 수 있다 | **DLQ 격리** |

</details>

---

## 이 lab이 다루지 않는 것

| 주제 | 다루는 곳 |
|------|----------|
| Kafka 설정 깊이 파기 (acks, commit 전략, rebalancing) | kafka-lab |
| 파티션 수 vs Consumer 수 역학, 리밸런싱 동작 | kafka-lab |
| DLQ 토픽 설계, retry backoff 전략, ErrorHandler 구성 | kafka-lab |
| Exactly-Once Semantics 동작과 한계 | kafka-lab |
| Consumer lag 모니터링, 운영 지표 | kafka-lab |
| CDC (Debezium) 기반 Outbox 릴레이 | 별도 주제 |
| Saga Pattern (Choreography/Orchestration), 보상 트랜잭션 | saga-lab (예정) |
