# Step 4 — Redis Pub/Sub

---

## Step 3의 한계에서 시작하자

Step 3에서 Event Store로 이벤트 유실을 해결했다. 서버가 죽어도 PENDING이 DB에 남아있고, 스케줄러가 재처리한다.

근데 전부 **같은 프로세스 안에서** 일어나는 일이다.

```
주문 서비스 (모놀리식)
├── OrderService → event_records에 PENDING 기록
├── 스케줄러 → PENDING 조회 → PointService 호출 → PROCESSED
└── 전부 같은 JVM, 같은 DB
```

포인트 서비스가 별도 시스템이면? 정산 시스템이 같은 이벤트를 소비해야 하면? 분석 시스템도? **ApplicationEvent는 프로세스 경계를 넘지 못한다.**

프로세스 밖으로 이벤트를 보내야 한다. 가장 단순한 방법부터 해보자. Redis Pub/Sub.

---

## 프로세스 밖으로 보내는 가장 단순한 방법

Redis Pub/Sub은 채널에 메시지를 발행하면, 그 채널을 구독하고 있는 모든 클라이언트가 메시지를 받는다. **다른 프로세스에서 돌고 있어도.**

```mermaid
sequenceDiagram
    participant Pub as Publisher (주문 서비스)
    participant Redis as Redis
    participant Sub as Subscriber (포인트 서비스)

    Sub->>Redis: SUBSCRIBE order-events
    Pub->>Redis: PUBLISH order-events "ORDER_CREATED"
    Redis-->>Sub: "ORDER_CREATED"
```

프로세스가 달라도, 서버가 달라도, Redis를 통해 메시지가 전달된다. **프로세스 경계를 넘었다.**

> **RedisPubSubBasicTest** — `발행한_메시지를_구독자가_수신한다()`에서 확인.

그리고 구독자가 여러 개여도 **전부 같은 메시지를 받는다.** 브로드캐스트다.

```mermaid
sequenceDiagram
    participant Pub as Publisher
    participant Redis as Redis
    participant S1 as 정산 서비스
    participant S2 as 알림 서비스
    participant S3 as 분석 서비스

    S1->>Redis: SUBSCRIBE order-events
    S2->>Redis: SUBSCRIBE order-events
    S3->>Redis: SUBSCRIBE order-events

    Pub->>Redis: PUBLISH order-events "ORDER_CREATED"
    Redis-->>S1: "ORDER_CREATED"
    Redis-->>S2: "ORDER_CREATED"
    Redis-->>S3: "ORDER_CREATED"
```

> **RedisPubSubBroadcastTest** — `여러_구독자가_동일한_메시지를_모두_수신한다()`에서 확인.

좋다. 프로세스 밖으로 보내는 것도 되고, 여러 시스템이 동시에 받는 것도 된다.

**그런데.**

---

## 구독자가 없으면 메시지는 사라진다

포인트 서비스가 아직 안 떠있는 상태에서 주문 이벤트를 발행하면?

```mermaid
sequenceDiagram
    participant Pub as Publisher
    participant Redis as Redis
    participant Sub as Subscriber

    Note over Sub: 아직 구독 안 함

    Pub->>Redis: PUBLISH order-events "ORDER_CREATED"
    Note over Redis: 전달 대상 없음 → 즉시 폐기

    Sub->>Redis: SUBSCRIBE order-events
    Note over Sub: 이제 구독 시작

    Note over Sub: "ORDER_CREATED"는<br/>영영 받을 수 없다
```

> **RedisPubSubMessageLossTest** — `구독자가_없으면_발행된_메시지는_유실된다()`에서 확인.

Redis Pub/Sub은 **메시지를 저장하지 않는다.** 발행 시점에 구독하고 있는 클라이언트에게 전달하고, 전달할 대상이 없으면 버린다.

---

## 구독자가 잠깐 죽었다가 살아나면?

더 현실적인 시나리오를 보자. 포인트 서비스가 정상 운영 중이다가 배포 때문에 잠깐 내려갔다.

```mermaid
sequenceDiagram
    participant Pub as Publisher
    participant Redis as Redis
    participant Sub as Subscriber

    rect rgb(230, 255, 230)
        Note over Sub: Phase 1: 정상 구독
        Sub->>Redis: SUBSCRIBE
        Pub->>Redis: PUBLISH "메시지1"
        Redis-->>Sub: "메시지1"
    end

    rect rgb(255, 230, 230)
        Note over Sub: Phase 2: 배포 중 (다운)
        Sub->>Redis: UNSUBSCRIBE
        Pub->>Redis: PUBLISH "메시지2"
        Note over Redis: 전달 대상 없음 → 폐기
    end

    rect rgb(230, 240, 255)
        Note over Sub: Phase 3: 배포 완료 (재구독)
        Sub->>Redis: SUBSCRIBE
        Pub->>Redis: PUBLISH "메시지3"
        Redis-->>Sub: "메시지3"
    end
```

수신한 메시지: `["메시지1", "메시지3"]`. **"메시지2"는 영영 사라졌다.**

> **RedisPubSubMessageLossTest** — `구독자가_다운된_동안_발행된_메시지는_수신할_수_없다()`에서 확인.

포인트 서비스가 배포되는 30초 동안 발생한 주문 이벤트는 전부 유실됐다. 포인트가 적립되지 않았고, **재처리할 방법이 없다.** Redis에 기록이 안 남아있으니까.

---

## 왜 이런 한계가 있는가

Redis Pub/Sub이 메시지를 유실하는 근본 원인은 Push/Pull 모델의 차이가 아니라 **메시지를 보존하지 않기 때문**이다.

```
메시지를 보존하지 않는 것:
  Redis Pub/Sub — 전달하고 끝. 기록 없음.

메시지를 보존하는 것:
  RabbitMQ — 큐에 저장. 소비하면 삭제. (Step 5)
  Kafka — 로그에 보존. 소비해도 남아있음. (Step 6)
```

Push 모델이라서 유실되는 게 아니다. **보존 안 하기 때문에** 유실되는 거다.

(참고: Redis에는 Pub/Sub 말고 **Redis Streams**라는 자료구조도 있다. 메시지 보존, Consumer Group, ACK를 지원한다. 이 Step에서는 가장 기본적인 메시징인 Pub/Sub만 다룬다.)

---

## 그런데 — Event Store를 이미 만들었잖아?

Step 3에서 Event Store를 만들었다. Redis Pub/Sub이 유실돼도 **PENDING이 DB에 남아있다.** 둘을 조합하면?

```
Event Store + Redis Pub/Sub 조합:
  1. 주문 저장 + 이벤트 기록 (같은 TX, PENDING)
  2. Redis PUBLISH로 "새 이벤트 있다" 신호 발송
  3. 포인트 서비스가 신호를 받으면 즉시 처리
  4. 신호를 못 받았어도 PENDING이 DB에 남아있다
  5. 스케줄러가 주기적으로 PENDING 조회 → 재처리
```

이 조합에서 Redis Pub/Sub은 **"즉시성을 위한 알림 채널"**이고, Event Store가 **"유실 방지의 안전망"**이다. Redis 메시지가 유실돼도 스케줄러가 잡아주니까, **유실 자체는 문제가 안 된다.** 규모가 작으면 이 조합으로 충분한 경우도 있다.

그러면 왜 여기서 멈추지 않고 RabbitMQ(Step 5), Kafka(Step 6)로 가는가? **Event Store + Redis Pub/Sub으로는 안 되는 것**이 있기 때문이다.

```
Event Store + Redis Pub/Sub으로 안 되는 것:
  독립적 소비 — 정산은 여기까지, 분석은 저기까지 (각자의 offset)
  순서 보장 — 같은 주문의 이벤트가 순서대로 처리되어야 할 때
  이벤트 재생 — "어제 이벤트를 처음부터 다시 소비해야 해"
  백프레셔 — Consumer가 느려도 메시지가 유실되지 않고 쌓여야 할 때
```

이게 Step 5(RabbitMQ)와 Step 6(Kafka)가 필요한 진짜 이유다.

---

## Redis Pub/Sub이 적합한 경우

Redis Pub/Sub **단독으로** 쓸 때 적합한 곳:

```
유실돼도 괜찮은 신호:
  캐시 무효화 — 못 받으면 TTL이 만료될 때까지 기다리면 됨
  실시간 알림 브로드캐스트 — 못 받으면 새로고침하면 됨
  서버 간 상태 동기화 — 다음 주기에 동기화하면 됨
```

Event Store와 조합하면 범위가 넓어지지만, 독립적 소비/순서 보장/이벤트 재생이 필요하면 메시지 브로커(RabbitMQ, Kafka)가 필요하다.

---

## 이 Step에서 일어난 일을 정리하면

```
Redis Pub/Sub:
  ✅ 프로세스 경계를 넘어 메시지 전달
  ✅ 여러 구독자에게 브로드캐스트
  ❌ 메시지를 보존하지 않음
  ❌ 구독자 없으면 유실
  ❌ 구독자 다운 중 메시지 유실
  ❌ 독립적 소비, 순서 보장, 이벤트 재생 불가

Event Store + Redis Pub/Sub 조합:
  ✅ 유실은 Event Store 스케줄러가 잡아줌
  ❌ 독립적 소비, 순서 보장, 이벤트 재생은 여전히 안 됨
  → 규모가 작으면 이걸로 충분할 수 있다
  → 위 기능이 필요해지면 메시지 브로커로
```

---

## 스스로 답해보자

- Redis Pub/Sub으로 프로세스 경계를 넘어 메시지를 보낼 수 있는가?
- 구독자가 없을 때 발행한 메시지는 어디로 가는가?
- 포인트 서비스 배포 중(30초)에 발생한 주문 이벤트를 나중에 재처리할 수 있는가? (Redis 단독 vs Event Store 조합)
- Redis Pub/Sub이 메시지를 유실하는 근본 원인은 Push 모델 때문인가, 보존 안 하기 때문인가?
- Event Store + Redis Pub/Sub 조합으로 충분한 경우와 부족한 경우는?

> 답이 바로 나오면 Step 5로 넘어가자.
> 막히면 `RedisPubSubMessageLossTest`의 3단계 시나리오를 실행해서 확인하자.

---

## 다음 Step으로

프로세스 밖으로 보내는 건 됐다. 유실은 Event Store가 잡아줄 수 있다.
근데 **메시지가 보존되지 않으니까 독립적 소비, 순서 보장, 이벤트 재생이 안 된다.**

Step 5에서 RabbitMQ로 메시지를 **큐에 저장**하면, 구독자가 없어도 메시지가 남아있고, Consumer가 나중에 와서 읽을 수 있다. 근데 거기서 또 다른 한계를 만난다.