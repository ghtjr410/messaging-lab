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
- [이 lab이 다루지 않는 것](#이-lab이-다루지-않는-것)

---

## 프로젝트 소개

각 도구의 **"존재 이유"**를 체험하는 lab입니다.
동작 원리는 "다음 step이 왜 필요한지 납득할 수 있는 최소한"만 다룹니다.
특정 도구의 설정, 튜닝, 운영 전략은 다루지 않습니다. (→ kafka-lab)

**"잘 되는 것"보다 "안 되는 것"을 먼저 확인합니다.**
각 Step은 이전 Step의 한계를 직접 체험한 뒤, 그 한계를 해결하는 다음 도구로 넘어갑니다.
모든 테스트 이름이 곧 증명 명제입니다.

---

## 시작하기

### 필요 환경

- **Java 21** 이상
- **Docker** 실행 중 (Testcontainers가 Redis, Kafka 컨테이너를 자동으로 띄웁니다)

### 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 Step만 실행
./gradlew test --tests "com.example.messaging.step1_application_event.*"

# 특정 테스트 클래스만 실행
./gradlew test --tests "ApplicationEventBasicTest"
```

---

## 학습 구조

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

처음이라면 반드시 **Step 0부터 순서대로** 진행하세요. 각 Step은 이전 Step의 한계를 전제로 합니다.

---

## 학습 순서 가이드

| Step | 주제 | 핵심 질문 | 인프라 |
|:----:|------|----------|:------:|
| 0 | **Command vs Event** | 같은 인프라인데 왜 설계가 달라지는가? | 없음 |
| 1 | **Application Event** | 직접 호출 대신 이벤트를 쓰면 뭐가 좋은가? | Spring Event |
| 2 | **Transactional Event** | 트랜잭션과 이벤트 타이밍은 왜 중요한가? | Spring Event |
| 3 | **Event Store** | 서버가 죽으면 이벤트는 어디로 가는가? | H2 (JPA) |
| 4 | **Redis Pub/Sub** | 프로세스 밖으로 이벤트를 보내면? | Redis |
| 5 | **Kafka** | 메시지를 보존하면서 전달하려면? | Kafka |
| 6 | **Idempotent Consumer** | 같은 메시지가 2번 오면 어떻게 되는가? | Kafka |

### Step 0 — Command vs Event

Command와 Event의 본질적 차이를 구분합니다.
이 구분은 Step 3(DB 저장 대상), Step 5(토픽 설계)에서 계속 돌아옵니다.

- Command: "쿠폰을 발급해라" → 아직 일어나지 않은 일, 실패할 수 있음, 1:1 지시
- Event: "주문이 생성되었다" → 이미 확정된 사실, 발행자는 누가 듣는지 모름, 1:N 통지

### Step 1 — Application Event

직접 호출 방식의 결합도 문제를 체험하고, `ApplicationEventPublisher`로 전환 후 의존성이 제거되는 것을 확인합니다.

- 직접 호출: OrderService가 StockService, CouponService, PointService를 모두 알고 있음
- ApplicationEvent 전환 후 의존성 제거 확인
- **한계 발견:** `@EventListener` 내부 예외가 발행자 트랜잭션을 롤백시킨다

### Step 2 — Transactional Event + Eventual Consistency

`@TransactionalEventListener(phase = AFTER_COMMIT)`으로 안전한 타이밍을 확보합니다.
`@Async`로 응답 속도를 개선하지만, 실패가 보이지 않는 문제를 발견합니다.

> **이 Step에서 인식해야 할 것:** AFTER_COMMIT + @Async를 선택한 순간,
> Eventual Consistency를 수용한 것입니다.

- **한계 발견:** 서버가 재시작되면 메모리의 이벤트는 증발한다

### Step 3 — Event Store

도메인 저장과 이벤트 기록을 하나의 트랜잭션으로 묶어 유실을 방지합니다.
스케줄러(릴레이)가 PENDING 이벤트를 처리합니다.

- Event Store 테이블: event_id, event_type, payload, status, created_at
- 서버 재시작 후에도 PENDING 이벤트가 남아있어서 재처리 가능
- **Step 5에서 Kafka로 릴레이하면 Transactional Outbox Pattern 완성**

### Step 4 — Redis Pub/Sub (프로세스 밖 전달, 비보존)

이벤트가 프로세스 경계를 넘어 전달되는 것을 확인합니다.
구독자가 없으면 메시지는 증발합니다.

- 적합한 케이스: 캐시 무효화 신호, 실시간 알림 브로드캐스트
- **한계 발견:** 메시지가 저장되지 않아 재처리 불가

### Step 5 — Kafka (프로세스 밖 전달, 보존, Outbox 완성)

메시지가 로그로 보존되어 여러 Consumer Group이 독립적으로 소비합니다.

```
Step 3에서 한 것:  도메인 저장 + Event Store 기록 (같은 TX)
이 Step에서 추가:  릴레이가 Event Store → Kafka로 발행
합치면:           Transactional Outbox Pattern
```

- **한계 발견:** 같은 메시지가 2번 오면 포인트가 2번 적립된다

### Step 6 — Idempotent Consumer & Failure Isolation

At Least Once 환경에서 중복 소비를 방어하는 세 가지 멱등 패턴을 구현합니다.

| 패턴 | 적합한 상황 | 트레이드오프 |
|------|-----------|------------|
| event_handled(event_id PK) | 범용, 어떤 도메인이든 적용 가능 | 별도 테이블 필요, 조회 비용 |
| Upsert | 집계성 데이터 (조회수, 좋아요수) | 도메인 특성에 의존, 범용성 낮음 |
| version / updated_at 비교 | 순서 역전까지 방어해야 하는 경우 | 구현 복잡도 높음 |

처리 불가능한 메시지(poison pill)는 DLQ로 격리합니다.

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
| 보상 트랜잭션 설계 | 별도 주제 |
