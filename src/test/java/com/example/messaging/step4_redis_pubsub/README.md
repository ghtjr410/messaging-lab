# Step 4 - Redis Pub/Sub

> 프로세스 경계를 넘어 이벤트를 전달할 수 있다. 단, 듣고 있는 놈만 받는다.

---

## 이 Step에서 해결하는 문제

Step 3까지는 이벤트가 단일 프로세스 안에서만 순환했습니다.
정산 서비스, 알림 서비스, 분석 서비스에도 이벤트를 보내야 한다면?
**프로세스 경계를 넘어야 합니다.**

## Redis Pub/Sub의 특성

- 프로세스 경계를 넘어 메시지를 전달할 수 있다
- **메시지는 보존되지 않는다** — 구독 중인 클라이언트만 수신
- Fire-and-forget 모델
- 적합: 캐시 무효화 신호, 실시간 알림
- 부적합: 주문/결제 같은 유실 불가 이벤트

## 메시징 패턴: Fan-Out (브로드캐스트)

Redis Pub/Sub은 **Fan-Out** 패턴이다 — 모든 구독자가 같은 메시지를 받는다.

```
Publisher → Redis Channel → Subscriber A (정산)
                          → Subscriber B (알림)
                          → Subscriber C (분석)
```

이 패턴은 **"서로 다른 관심사가 같은 이벤트를 각자 처리"**할 때 적합하다.
반대 패턴인 **Competing Consumers** (같은 관심사의 여러 인스턴스가 메시지를 나눠 처리)는
Redis Pub/Sub으로는 불가능하다 — Step 5(Kafka Consumer Group)에서 다룬다.

| 패턴 | 목적 | Redis Pub/Sub | Kafka |
|------|------|:---:|:---:|
| **Fan-Out** | 서로 다른 관심사가 같은 이벤트를 각자 처리 | O (브로드캐스트) | O (Consumer Group별 독립 소비) |
| **Competing Consumers** | 같은 관심사의 인스턴스가 부하 분산 | X | O (같은 Group 내 파티션 분배) |

---

## 시퀀스 다이어그램

### 기본 Pub/Sub 흐름

```mermaid
sequenceDiagram
    participant Publisher as Publisher (프로세스 A)
    participant Redis
    participant Sub1 as Subscriber 1 (프로세스 B)
    participant Sub2 as Subscriber 2 (프로세스 C)

    Sub1->>Redis: SUBSCRIBE order-events
    Sub2->>Redis: SUBSCRIBE order-events
    Publisher->>Redis: PUBLISH order-events "주문생성"
    Redis-->>Sub1: "주문생성"
    Redis-->>Sub2: "주문생성"
    Note over Sub1,Sub2: 모든 활성 구독자가<br/>동시에 수신 (브로드캐스트)
```

### 메시지 유실: 구독자 없음

```mermaid
sequenceDiagram
    participant Publisher
    participant Redis
    participant Subscriber

    Note over Subscriber: 구독자 없음 (아직 시작 안 됨)
    Publisher->>Redis: PUBLISH order-events "주문생성"
    Note over Redis: 전달 대상 없음 -> 즉시 폐기

    Note over Subscriber: 나중에 구독 시작
    Subscriber->>Redis: SUBSCRIBE order-events
    Note over Subscriber: "주문생성" 메시지를<br/>받을 방법 없음
```

### 메시지 유실: 구독자 다운타임

```mermaid
sequenceDiagram
    participant Publisher
    participant Redis
    participant Subscriber

    Subscriber->>Redis: SUBSCRIBE order-events
    Publisher->>Redis: PUBLISH "메시지1"
    Redis-->>Subscriber: "메시지1"

    Note over Subscriber: 구독자 다운 (연결 끊김)
    Publisher->>Redis: PUBLISH "메시지2"
    Note over Redis: 전달 대상 없음 -> 폐기

    Note over Subscriber: 구독자 재시작
    Subscriber->>Redis: SUBSCRIBE order-events
    Publisher->>Redis: PUBLISH "메시지3"
    Redis-->>Subscriber: "메시지3"
    Note over Subscriber: "메시지2"는 영영 수신 불가
```

---

## 테스트 목록

| 테스트 클래스 | 메서드 | 증명하는 것 |
|---|---|---|
| RedisPubSubBasicTest | 발행한_메시지를_구독자가_수신한다 | 기본 동작 |
| RedisPubSubBroadcastTest | 여러_구독자가_동일한_메시지를_모두_수신한다 | 브로드캐스트 |
| RedisPubSubMessageLossTest | 구독자가_없으면_발행된_메시지는_유실된다 | 비보존 (구독자 없음) |
| RedisPubSubMessageLossTest | 구독자가_다운된_동안_발행된_메시지는_수신할_수_없다 | 비보존 (다운타임) |

## Testcontainer

이 Step은 Redis Testcontainer를 사용합니다. Docker가 실행 중이어야 합니다.

```
GenericContainer("redis:7-alpine") - port 6379
```

## 체험할 한계 -> Step 5로

메시지가 저장되지 않는다. 구독자가 없으면 증발한다.
"어제 이벤트를 다시 처리해야 해"라는 요구가 오면 불가능하다.
