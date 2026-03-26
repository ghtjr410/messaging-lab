# Step 6 — Kafka

---

## Step 5의 한계에서 시작하자

Step 5에서 RabbitMQ로 "메시지를 저장한다"는 문제를 해결했다. Consumer가 없어도 큐에 남아있고, 다운 중에 발행된 메시지도 재시작 후 받을 수 있었다.

근데 한 가지 근본적인 특성이 남아있다. **Consumer가 ACK하면 메시지가 큐에서 삭제된다.**

```
"어제 포인트 적립 로직에 버그가 있었어.
 어제 주문 이벤트를 처음부터 다시 처리해야 해."

→ RabbitMQ: 불가능. 소비하면서 삭제했으니까.
```

그리고 Consumer Group이라는 개념이 없다. 정산 시스템, 알림 시스템, 분석 시스템이 같은 이벤트를 **각자 독립적으로** 소비하려면 Exchange + 큐 복제라는 복잡한 설정이 필요하다.

Kafka는 이 두 문제를 동시에 해결한다. 어떻게?

Kafka를 흔히 "메시지 큐"라고 부르지만, 본질은 **분산 로그 저장소(Distributed Log Store)**에 더 가깝다. Redis Pub/Sub은 "전달"이 목적이고, RabbitMQ는 "큐잉"이 목적이지만, **Kafka의 핵심은 "적재"다.** 메시지를 디스크에 로그로 쌓고, Consumer는 각자의 offset으로 읽어가는 구조다.

이 차이가 모든 것을 바꾼다.

먼저 기본 동작을 확인하자. Producer가 메시지를 보내고, Consumer가 수신한다.

> **KafkaBasicPipelineTest** — `Producer가_보낸_메시지를_Consumer가_수신한다()`에서 기본 파이프라인을 확인.
> **KafkaBasicPipelineTest** — `여러_메시지를_순서대로_발행하면_같은_파티션에서_순서대로_소비된다()`에서 파티션 내 순서 보장을 확인.

---

## 소비해도 메시지가 사라지지 않는다

RabbitMQ와 가장 큰 차이부터 보자.

Kafka에서 Consumer가 없는 상태로 메시지를 발행하면?

```mermaid
sequenceDiagram
    participant Prod as Producer
    participant Kafka as Kafka
    participant Cons as Consumer (나중에 연결)

    Prod->>Kafka: msg1, msg2, msg3
    Note over Kafka: 구독자 없음<br/>메시지는 로그에 보존

    Note over Cons: 나중에 연결
    Cons->>Kafka: subscribe (from beginning)
    Kafka-->>Cons: msg1, msg2, msg3

    Note over Cons: 3건 모두 수신
```

> **KafkaMessagePreservationTest** — `구독자가_없어도_메시지는_Kafka에_보존된다()`에서 확인.

**Redis Pub/Sub이었다면 3건 전부 유실, RabbitMQ였다면 보존은 되지만 소비하면 삭제.** Kafka는 소비해도 로그에 남아있다. retention 기간(보통 7일) 동안 언제든 다시 읽을 수 있다.

Consumer가 중지됐다가 재시작하면?

```mermaid
sequenceDiagram
    participant Prod as Producer
    participant Kafka as Kafka
    participant CA as ConsumerA
    participant CB as ConsumerB

    rect rgb(230, 255, 230)
        Note over CA: Phase 1: 정상 소비
        Prod->>Kafka: msg1, msg2, msg3
        CA->>Kafka: poll → 3건 수신
        CA->>Kafka: commit (offset=3)
        Note over CA: 종료
    end

    rect rgb(255, 230, 230)
        Note over Kafka: Phase 2: Consumer 없음
        Prod->>Kafka: msg4, msg5
        Note over Kafka: 메시지 보존됨
    end

    rect rgb(230, 240, 255)
        Note over CB: Phase 3: 같은 Group으로 재시작
        CB->>Kafka: poll (offset 3부터)
        Kafka-->>CB: msg4, msg5
        Note over CB: 중지 중 발행된 메시지를<br/>이어서 읽기
    end
```

> **KafkaMessagePreservationTest** — `Consumer가_중지된_사이에_발행된_메시지를_재시작_후_이어서_읽는다()`에서 확인.

**중지 중에 발행된 메시지를 이어서 읽는다.** Kafka는 각 Consumer Group의 offset을 기억하니까, "어디까지 읽었는가"를 추적할 수 있다. 배포 30초 동안 발생한 이벤트가 유실되는 일은 없다.

RabbitMQ도 "Consumer 다운 중 보존"은 되지만, 핵심 차이는 이거다. **RabbitMQ는 소비하면 삭제, Kafka는 소비해도 남아있다.** "어제 이벤트를 처음부터 다시 처리해야 해"가 Kafka에서는 offset을 되돌리는 것만으로 가능하다.

---

## 여러 시스템이 독립적으로 소비한다

정산 시스템, 알림 시스템, 분석 시스템이 같은 주문 이벤트를 각자 소비해야 한다. RabbitMQ에서는 Exchange + 큐 복제가 필요했다. Kafka에서는 **Consumer Group**으로 간단히 해결된다.

```mermaid
sequenceDiagram
    participant Prod as Producer
    participant Kafka as Kafka
    participant GA as 정산 Group
    participant GB as 알림 Group

    Prod->>Kafka: msg1, msg2, msg3

    GA->>Kafka: poll
    Kafka-->>GA: msg1, msg2, msg3 (3건)

    GB->>Kafka: poll
    Kafka-->>GB: msg1, msg2, msg3 (3건)

    Note over GA,GB: 각 Group이 독립적으로<br/>모든 메시지를 수신
```

> **KafkaConsumerGroupIndependenceTest** — `두_Consumer_Group이_같은_토픽의_모든_메시지를_각각_독립적으로_수신한다()`에서 확인.

각 Group이 **자기만의 offset**을 관리하니까, 한 Group이 느려도 다른 Group에 영향이 없다.

```mermaid
sequenceDiagram
    participant Prod as Producer
    participant Kafka as Kafka
    participant Slow as slow-group
    participant Fast as fast-group

    rect rgb(230, 255, 230)
        Prod->>Kafka: msg1, msg2, msg3
        Slow->>Kafka: poll → 3건 + commit (offset=3)
    end

    rect rgb(255, 245, 230)
        Prod->>Kafka: msg4, msg5
        Note over Slow: slow-group은 아직 읽지 않음
    end

    Fast->>Kafka: poll (from beginning)
    Kafka-->>Fast: msg1~msg5 (5건 전부)

    Slow->>Kafka: poll (offset 3부터)
    Kafka-->>Slow: msg4, msg5 (2건)

    Note over Slow,Fast: fast-group: 5건<br/>slow-group: 2건 (이어서 읽기)<br/>속도 독립성
```

> **KafkaConsumerGroupIndependenceTest** — `한_Consumer_Group의_소비_속도가_다른_Group에_영향을_주지_않는다()`에서 확인.

Redis Pub/Sub의 브로드캐스트와 비슷해 보이지만 결정적 차이가 있다. Redis는 메시지가 보존되지 않으니까 느린 Consumer가 놓치면 끝이다. RabbitMQ는 각 큐에 복제해야 하니까 설정이 복잡하다. Kafka는 하나의 로그를 여러 Group이 **각자의 속도로** 읽는 구조라서, 추가 설정 없이 독립적 소비가 된다.

---

## 순서가 보장되는 범위를 알아야 한다

Kafka에서 순서 보장은 **토픽 전체가 아니라 파티션 단위**다. 이 구분을 모르면 "순서가 보장된다고 했는데 왜 뒤집히지?"가 된다.

같은 key로 보내면 같은 파티션에 들어간다.

```mermaid
sequenceDiagram
    participant Prod as Producer
    participant Kafka as Kafka (3 partitions)
    participant Cons as Consumer

    Prod->>Kafka: key="order-1001", "생성"
    Prod->>Kafka: key="order-1001", "결제"
    Prod->>Kafka: key="order-1001", "배송"

    Note over Kafka: 3건 모두 같은 파티션

    Cons->>Kafka: poll
    Note over Cons: 순서: 생성 → 결제 → 배송
```

> **KafkaPartitionOrderingTest** — `같은_key의_메시지는_같은_파티션에_저장된다()`와 `같은_파티션의_메시지는_발행_순서대로_소비된다()`에서 확인.

다른 key는 다른 파티션으로 갈 수 있다. 그러면 서로 다른 주문 사이에는 순서가 보장되지 않는다.

```mermaid
sequenceDiagram
    participant Prod as Producer
    participant Kafka as Kafka (3 partitions)

    Prod->>Kafka: key="order-0" ~ "order-9" (10건)

    Note over Kafka: Partition 0: [order-X, order-Y, ...]
    Note over Kafka: Partition 1: [order-Z, order-W, ...]
    Note over Kafka: Partition 2: [order-A, order-B, ...]

    Note over Kafka: 다른 key는 다른 파티션으로<br/>분배될 수 있다
```

> **KafkaPartitionOrderingTest** — `다른_key의_메시지는_다른_파티션으로_분배될_수_있다()`에서 확인.

그래서 **partition key 설계**가 중요하다. 같은 주문의 이벤트가 순서대로 처리돼야 하면 `orderId`를 key로 쓴다. 같은 상품의 이벤트가 순서대로 처리돼야 하면 `productId`를 key로 쓴다.

key 설계에서 흔히 틀리는 것: **공유 자원이 뭔지를 잘못 짚는 것이다.** 예를 들어 선착순 쿠폰 발급에서 key를 `userId`로 잡으면 같은 유저의 요청만 같은 파티션으로 간다. 근데 충돌이 나는 공유 자원은 "유저"가 아니라 "쿠폰 수량"이다. `couponId`를 key로 잡아야 같은 쿠폰에 대한 요청이 같은 파티션 → 같은 Consumer → 순차 처리가 된다. **동시성 문제를 락으로 막는 게 아니라, 설계로 발생 조건을 없애는 방식이다.**

단, partition key 하나에 트래픽이 극단적으로 몰리면 **Hot Partition**이 된다. 10만 명이 같은 쿠폰에 동시에 몰리면 해당 파티션 하나에 부하가 집중된다. 이 문제의 구체적인 해결 전략(Key Sharding, Redis 선착순 컷 등)은 kafka-lab에서 다룬다.

---

## 이제 Outbox를 완성하자

여기까지 Kafka의 기본 특성을 봤다. 이제 Step 3에서 만든 Event Store와 합칠 시간이다.

```
Step 3에서 한 것:
  도메인 저장 + 이벤트 기록 = 같은 TX (원자성)
  스케줄러가 PENDING 조회 → 같은 프로세스에서 처리 → PROCESSED

이 Step에서 바꾸는 것:
  스케줄러가 PENDING 조회 → Kafka로 발행 → SENT
  별도 Consumer가 Kafka에서 읽어서 처리
```

합치면 **Transactional Outbox Pattern**이다.

```mermaid
sequenceDiagram
    participant OS as OrderService
    participant DB as orders
    participant OB as outbox_events
    participant Relay as 릴레이
    participant Kafka as Kafka
    participant Cons as Consumer

    Note over OS: TX BEGIN
    OS->>DB: INSERT 주문
    OS->>OB: INSERT 이벤트 (PENDING)
    Note over OS: TX COMMIT

    Relay->>OB: SELECT WHERE status = 'PENDING'
    Relay->>Kafka: PUBLISH (key=orderId)
    Relay->>OB: UPDATE status = 'SENT'

    Cons->>Kafka: poll
    Cons->>Cons: 포인트 적립
```

> **TransactionalOutboxCompletionTest** — `주문_저장과_이벤트_기록이_하나의_트랜잭션으로_묶인다()`에서 원자성을 확인.
> **TransactionalOutboxCompletionTest** — `릴레이가_PENDING_이벤트를_Kafka로_발행하고_SENT로_변경한다()`에서 릴레이를 확인.

### 왜 Kafka 발행을 트랜잭션 안에서 하지 않는가

DB와 Kafka는 서로 다른 시스템이다. **하나의 트랜잭션으로 원자성을 보장할 수 없다.** 어떤 순서로 하든 문제가 생긴다.

```
순서 1: TX 안에서 Kafka 먼저 발행 → DB 커밋
  Kafka 발행 성공 → DB 롤백
  → 원본 데이터는 없는데 이벤트가 Kafka에 전파됨

순서 2: TX 안에서 DB 커밋 → Kafka 발행
  DB 커밋 성공 → Kafka 발행 실패
  → 원본 데이터는 있는데 이벤트가 전파 안 됨
```

어느 쪽이든 정합성이 깨진다. 이건 Kafka만의 문제가 아니라 **트랜잭션 안에서 외부 I/O(Kafka, Redis, 외부 API)를 하면 안 되는** 근본적 이유다. 두 시스템 간에는 원자성이 없으니까.

그래서 DB에 먼저 기록하고(같은 TX), 실제 Kafka 발행은 별도 프로세스(릴레이)로 수행하는 것이다. 이게 Outbox Pattern의 핵심이다.

### Kafka 발행이 실패하면?

PENDING 상태가 유지된다. 다음 릴레이 실행 시 재시도할 수 있다.

```mermaid
sequenceDiagram
    participant Relay as 릴레이
    participant OB as outbox_events
    participant Kafka as Kafka (연결 불가)

    Relay->>OB: SELECT WHERE status = 'PENDING'
    Relay->>Kafka: PUBLISH 시도
    Kafka--xRelay: 연결 실패!

    Note over OB: 여전히 PENDING
    Note over OB: 다음 실행 시 재시도 가능
```

> **TransactionalOutboxCompletionTest** — `Kafka_발행_실패_시_이벤트는_여전히_PENDING_상태를_유지한다()`에서 확인.

### 그런데 Kafka 발행이 성공한 뒤에 죽으면?

여기가 핵심이다. 릴레이가 Kafka에 발행하는 과정을 자세히 보자.

```
① PENDING 조회
② kafka.send() — 성공 (Kafka에 적재됨)
③ outbox status = SENT 로 변경 — 💀 여기서 서버가 죽으면?
```

```mermaid
sequenceDiagram
    participant Relay as 릴레이
    participant OB as outbox (DB)
    participant Kafka as Kafka

    Relay->>OB: SELECT WHERE status = 'PENDING'
    OB-->>Relay: msg-001

    Relay->>Kafka: send(msg-001)
    Kafka-->>Relay: ACK (적재 완료)

    Note over Relay: ② 성공. 이제 SENT로 바꾸려는데...
    Note over Relay: 💀 서버 죽음!

    Note over OB: 여전히 PENDING
    Note over Kafka: msg-001 이미 적재됨
```

Kafka에는 이미 들어갔는데, outbox에는 아직 PENDING이다. 릴레이가 재시작하면 PENDING을 다시 조회해서 **같은 메시지를 또 발행한다.** Kafka에 msg-001이 2건이 된다.

**"발행이 안 된 건 아니다. 발행됐다는 사실을 기록 못 한 것이다."**

그러면 순서를 바꿔서 SENT를 먼저 갱신하면?

```
① PENDING 조회
② outbox status = SENT 로 변경
③ kafka.send() — 💀 여기서 서버가 죽으면?
→ SENT인데 Kafka에는 안 들어감 → 메시지 유실
```

**중복보다 유실이 훨씬 위험하다.** 중복은 Consumer 멱등으로 막을 수 있지만, 유실은 복구할 방법이 없다. 그래서 **"발행 먼저, 상태 갱신 나중에"** 순서를 선택한다.

이게 **At Least Once 발행 보장**이다. 0번 전달은 절대 없고, 2번은 있을 수 있다. 그리고 그 "2번"을 Step 7의 멱등 처리가 막아준다. 합치면 **effectively exactly-once**가 된다.

### 자주 혼동되는 것 — Idempotent Producer와 Outbox는 다른 문제를 해결한다

```
Kafka Idempotent Producer (enable.idempotence=true):
  Producer가 리트라이하다가 같은 메시지가 브로커에 2번 들어가는 걸 막는다.
  → 브로커 내부의 안전 장치.

Outbox Pattern:
  애플리케이션이 DB는 커밋했는데 Kafka 발행 자체를 못 한 경우를 막는다.
  → 애플리케이션 레벨의 안전 장치.
```

Idempotent Producer를 켜도 "발행 자체가 안 된" 상황은 커버 못 한다. Outbox가 "반드시 발행한다"를 보장하고, Idempotent Producer가 "브로커 안에서 중복을 막는다"를 보장한다. **둘은 보장 범위가 다른 보완재다.**

---

## 모든 이벤트에 Outbox를 써야 하는가? — 아니다

Outbox를 완성했다. 그러면 모든 이벤트에 다 적용해야 하는가?

**아니다.** Outbox 패턴을 도입하면 운영 복잡도가 생긴다.

```
1. DB 의존 증가
   이벤트 발행을 위해 메시지 브로커를 도입했는데,
   Outbox로 인해 이벤트 발행마저 DB에 의존하는 구조가 된다.

2. 쓰기 부하 증가
   비즈니스 로직 쓰기 + Outbox 쓰기가 같은 트랜잭션에서 발생한다.
   트랜잭션이 길어지고 DB 커넥션 점유 시간이 늘어난다.

3. 릴레이 프로세스 관리
   Outbox를 폴링해서 Kafka로 릴레이하는 별도 프로세스를 운영해야 한다.
   이 프로세스가 죽으면 발행이 밀린다.
```

이 복잡도를 **모든 이벤트에 적용하는 건 과도한 엔지니어링**이다. 그렇다고 "이벤트의 중요도"로 적용 여부를 판단하는 것도 틀렸다. **이벤트는 사실이고, 사실에 등급을 매기는 건 Producer의 권한이 아니다.** Producer는 Consumer가 이 이벤트를 어떻게 쓸지 모른다. "좋아요 이벤트는 유실돼도 괜찮다"고 Producer가 판단하는 순간, Step 0의 "Event는 수신자를 모른다"는 원칙을 위반한다. 어떤 팀에게는 좋아요 이벤트가 핵심 도메인일 수 있다.

그러면 어떤 기준으로 Outbox 적용 여부를 결정하는가? **2층 구조**로 접근한다.

### Layer 1 — 이벤트 인프라 기본 신뢰성 (모든 이벤트에 적용)

**모든 이벤트에 대해** 발행 인프라의 기본 신뢰성을 충분히 높인다. "이벤트 중요도에 따라 차등"이 아니라, 인프라 자체의 보장 수준을 올리는 것이다.

```
Producer:
  acks=all                      — ISR 전체 복제 후 ACK
  enable.idempotence=true       — 브로커 내 중복 방지
  retries 충분                   — 일시적 실패 시 재시도
  delivery.timeout.ms 설정       — retry의 전체 시간 상한 (retries 횟수만으로 부족)

Broker:
  replication.factor=3          — 3벌 복제
  min.insync.replicas=2         — 최소 2벌 동기화 후 ACK

Consumer:
  enable.auto.commit=false      — 처리 완료 후 수동 offset commit
```

이 설정만으로 **"Kafka가 정상인 한"** 이벤트 유실은 거의 없다. 유실이 발생하는 건 **"Kafka 전송 자체가 실패"**하는 경우 — 프로세스가 죽거나, Kafka 클러스터가 장시간 불능이거나, 네트워크 단절이 지속되는 경우다.

### Layer 2 — Outbox 적용 기준 (선별 적용)

Layer 1이 커버하지 못하는 영역이 있다. **DB 트랜잭션은 커밋됐는데 Kafka 전송이 실패하는** 경우다. 이때 Outbox가 필요한지의 기준은 "이벤트의 중요도"가 아니라 **"DB 상태 변경과 이벤트 발행 사이에 원자성이 필요한가"**다. 이건 Consumer의 사정이 아니라 **Producer 자신의 데이터 정합성 문제**다.

```
질문: "DB 트랜잭션 커밋은 됐는데 Kafka 전송이 실패하면
       비즈니스 정합성이 깨지는가?"

Yes → Outbox (DB 상태와 이벤트 발행의 원자성이 필요)
No  → Layer 1으로 충분 (DB 상태와 이벤트가 강결합이 아님)
```

| 이벤트 | DB-이벤트 원자성 필요? | 이유 | 전략 |
|--------|:---:|------|------|
| 주문 생성 | Yes | 주문 저장 + 이벤트 발행이 함께 성공해야. 이벤트 유실 시 결제/배송 미진행 | Outbox |
| 결제 완료 | Yes | 결제 상태 변경 + 이벤트 발행이 원자적이어야. 불일치 시 이중결제/미정산 | Outbox |
| 좋아요 클릭 | 구현에 따라 다름 | 아래 참고 | Layer 1 또는 Outbox |
| 페이지 조회 | No | DB 상태 변경 자체가 없음. 이벤트만 발행 | Layer 1 |

> 좋아요의 판단은 **구현 구조에 따라 달라진다.**
> - API가 DB에 `like_count`를 갱신하고 + 이벤트를 발행하는 구조 → DB 상태와 이벤트 불일치 가능 → 원자성 필요 → **Outbox**
> - API가 이벤트만 발행하고, 집계는 Consumer가 하는 구조 → DB 상태 변경 없음 → **Layer 1으로 충분**

이 기준은 "좋아요가 덜 중요해서"가 아니다. **"DB-이벤트 간 원자성이라는 기술적 제약이 존재하느냐"**의 문제다. Producer가 Consumer의 중요도를 대신 판단하는 것이 아니라, Producer 자신의 정합성 구조를 보는 것이다.

### Outbox 없이 직접 발행하는 구조

DB 상태와 이벤트 발행이 독립적인 경우, `@TransactionalEventListener(AFTER_COMMIT)` + 직접 Kafka 발행으로 충분하다.

```mermaid
sequenceDiagram
    participant API as API 서버
    participant DB as Database
    participant K as Kafka

    API->>DB: INSERT (비즈니스 로직)
    Note over DB: TX 커밋

    API->>K: send(event) — AFTER_COMMIT
    Note over K: 발행 성공 (대부분의 경우)

    Note over API: 서버가 여기서 죽으면?<br/>이벤트 유실.<br/>DB 상태와 이벤트가 독립적이면<br/>정합성 문제 없음.
```

Step 2에서 봤듯이 AFTER_COMMIT은 메모리 기반이라 프로세스가 죽으면 유실된다. **DB 상태와 이벤트 발행이 독립적이라면** 이 구조로 충분하고, Outbox의 복잡도를 감수할 이유가 없다.

### Outbox Table ≠ Event Store

혼동하기 쉬운 두 개념을 구분한다.

```
Outbox Table:
  발행 보장용 중간 버퍼. 발행 후 SENT 마킹. 일시적.
  목적: "DB 커밋과 이벤트 발행 사이의 원자성 보장"

Event Store:
  시스템 상태의 원천. 영구 보관. 조회 및 재생 가능. (Event Sourcing)
  목적: "이벤트가 곧 상태"
```

Step 3의 `EventRecord`가 Step 6에서 `OutboxEvent`로 이름이 바뀐 것은 역할이 "이벤트를 DB에 기록한다"에서 "발행 보장 버퍼"로 전환됐기 때문이다. Outbox를 "아키텍처의 핵심"으로 격상하면, 그건 이미 Event Sourcing 방향으로 가고 있는 것이다. 이 lab에서 Outbox는 말 그대로 **발행 보장용 중간 장치**다.

### Outbox 릴레이의 운영 한계

Outbox 릴레이(스케줄러)는 PENDING을 주기적으로 폴링해서 Kafka에 발행한다. 이 구조에는 두 가지 한계가 있다.

**분산 한계:** API 서버가 2대로 늘면 스케줄러도 2개가 돈다. 같은 PENDING을 동시에 잡아서 중복 발행한다. 릴레이는 API 서버와 분리해서 단일 인스턴스로 운영하는 게 기본이다. API는 스케일 아웃, 릴레이는 단일 인스턴스 — 이렇게 분리해야 API 확장이 릴레이에 의해 제약받지 않는다.

**규모 한계:** 피크 타임에 PENDING이 수만 건 쌓이면 폴링 주기 안에 처리를 못 한다. 다음 주기가 돌아오면서 이전 건과 중복 처리가 쌓이고, 더 큰 장애가 된다.

규모가 커지면 릴레이 방식을 진화시킨다.

| 단계 | 방식 | 적합한 규모 |
|------|------|-----------|
| 1 | 단일 인스턴스 폴링 | 소규모 (수백 TPS 이하) |
| 2 | 분산 락(ShedLock 등) + 다중 인스턴스 폴링 | 중규모 |
| 3 | CDC (Debezium 등, binlog 기반) | 대규모 — 폴링 자체를 없앤다 |

이 lab에서는 단계 1만 다룬다. CDC는 별도 주제다.

---

## 여기까지 온 길을 돌아보면

```
Step 1: ApplicationEvent로 분리 → 같은 TX라서 리스너 실패 시 롤백
Step 2: AFTER_COMMIT + @Async → 안전하지만 메모리 휘발
Step 3: Event Store → DB에 기록해서 유실 방지, 근데 같은 프로세스
Step 4: Redis Pub/Sub → 프로세스 밖 전달, 근데 메시지 비보존
Step 5: RabbitMQ → 큐에 저장, 근데 소비하면 삭제 (재처리 불가)
Step 6: Kafka → 소비해도 로그에 남음 + 재처리 가능
        + Step 3 Event Store 합치면 = Transactional Outbox Pattern
```

각 Step이 이전 Step의 한계를 해결하면서 여기까지 왔다. **Kafka가 갑자기 등장한 게 아니라, Redis → RabbitMQ → Kafka로 한 단계씩 한계를 넘으면서 자연스럽게 도달한 것이다.**

---

## 스스로 답해보자

- RabbitMQ에서 ACK하면 메시지가 삭제되는데, Kafka에서는 왜 남아있는가?
- Consumer Group A가 느려도 Group B에 영향이 없는 이유는?
- "순서 보장"이 토픽 전체가 아니라 파티션 단위인 이유는?
- 선착순 쿠폰 발급에서 key를 `userId`가 아니라 `couponId`로 잡아야 하는 이유는?
- Outbox Pattern에서 Kafka 발행을 왜 트랜잭션 안에서 하지 않는가? (양쪽 시나리오를 말할 수 있는가?)
- "발행 먼저, 상태 갱신 나중에" 순서를 선택한 이유는?
- Idempotent Producer와 Outbox Pattern이 해결하는 문제가 어떻게 다른가?
- At Least Once 발행이 보장되면, Consumer 쪽에서는 어떤 문제가 생기는가?
- 좋아요 이벤트에 Outbox를 적용해야 하는가? 판단 기준은 무엇인가?
- Outbox 릴레이를 API 서버와 분리해야 하는 이유는?

> 마지막 질문의 답이 Step 7의 존재 이유다.
> 답이 바로 나오면 Step 7로 넘어가자.

---

## 참고

| 주제 | 링크 |
|------|------|
| Kafka 공식 문서 | [Apache Kafka Documentation](https://kafka.apache.org/documentation/) |
| Transactional Outbox Pattern | [Microservices.io — Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html) |
| 쿠팡 마이크로서비스 전환 (비타민 MQ) | [마이크로서비스 아키텍처로의 전환 — 쿠팡 엔지니어링](https://medium.com/coupang-engineering/how-coupang-built-a-microservice-architecture-fd584fff7f2b) |
| 배민 포인트 시스템 (SQS 비동기) | [신규 포인트 시스템 전환기 #1 — 우아한형제들 기술블로그](https://woowabros.github.io/experience/2018/10/12/new_point_story_1.html) |

---

## 다음 Step으로

At Least Once 발행을 보장하면, **같은 메시지가 2번 올 수 있다.**

릴레이 쪽: kafka.send() 성공 → SENT 갱신 전에 죽음 → 재시작 시 다시 발행 → **중복 발행.**
Consumer 쪽: 메시지 처리 성공 → offset 커밋 전에 죽음 → 재시작 시 다시 읽음 → **중복 소비.**

**같은 구조의 문제다.** "작업 + 상태 갱신 사이의 간극"이 Producer에서는 "send + SENT", Consumer에서는 "처리 + offset 커밋"으로 나타난다. 어느 쪽이든 **포인트가 2번 적립되거나, 쿠폰이 2번 발급된다.**

Step 7에서 이 중복을 방어하는 멱등 패턴을 구현한다.