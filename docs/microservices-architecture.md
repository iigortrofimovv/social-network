# Microservices Architecture

My thoughts on how I would split this monolith if it needed to scale.

---

## What we have now

The monolith already has three clear bounded contexts — they're just in the same process sharing one database:

- **User Management** — creating profiles, storing metadata
- **Interaction** — visits and likes
- **Fraud Detection** — async analysis, marking users

The main coupling problem is `FraudService`: it reads from `profile_visits` and `profile_likes`, then writes to
`users.is_fraud`. In a proper microservices setup a service can't touch another service's database directly. That's the
main thing to solve.

---

## Proposed split

```
                        ┌─────────────┐
                        │ API Gateway  │
                        └──────┬──────┘
               ┌───────────────┼───────────────┐
               ▼               ▼               ▼
        ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
        │User Service │ │Interaction  │ │Fraud Service│
        │             │ │Service      │ │             │
        │users DB     │ │visits DB    │ │fraud DB     │
        └─────────────┘ └──────┬──────┘ └──────┬──────┘
                               │               │
                               └───────┬───────┘
                                       ▼
                                    Kafka
```

Each service owns its own database — no shared tables.

---

## Services

### User Service

Owns the `users` table. Handles profile creation and exposes fraud status to other services.

Public API:

```
POST /api/v1/users
GET  /api/v1/users/{id}
```

Internal API (not exposed through gateway, cluster-internal only):

```
GET   /internal/v1/users/{id}/fraud-status
PATCH /internal/v1/users/{id}/fraud-status
```

### Interaction Service

Owns `profile_visits` and `profile_likes`. Records actions and serves the visitors query.

```
POST /api/v1/user/visit
POST /api/v1/user/visit/bulk
GET  /api/v1/user/{userId}/visitors
POST /api/v1/user/like
POST /api/v1/user/like/bulk
```

Publishes to Kafka after each action:

```json
{
  "userId": 42,
  "type": "VISIT",
  "targetId": 17,
  "occurredAt": "2025-01-15T14:32:00Z"
}
```

### Fraud Service

Consumes user action events, runs the detection query against its own aggregated data, publishes a verdict.

Has its own `fraud_records` table — doesn't touch the users DB.

Subscribes to: `user-actions`  
Publishes to: `fraud-detected`

```json
{
  "userId": 42,
  "detectedAt": "2025-01-15T14:32:01Z",
  "reason": "100 actions within first 10 minutes"
}
```

---

## API Versioning

I'd go with URI versioning — `/api/v1/`, `/api/v2/`. It's the most straightforward option: visible in logs, easy to
route in the gateway, easy to test.

When breaking changes are needed, the old version stays alive for a deprecation period (I'd say at least 3-6 months
depending on how many clients there are). The deprecated version returns extra headers:

```
Deprecation: true
Sunset: 2026-07-01T00:00:00Z
```

Non-breaking changes (adding optional fields, new endpoints) don't need a new version — just do them in place.

---

## Circuit Breaking

Between services I'd use Resilience4j. The key place is Interaction Service calling User Service to check fraud status:

```java

@CircuitBreaker(name = "user-service", fallbackMethod = "isFraudFallback")
public Boolean isFraud(Long userId) {
    return restTemplate.getForObject(".../fraud-status", Boolean.class, userId);
}

private Boolean isFraudFallback(Long userId, Exception ex) {
    log.error("User service down, falling back for userId={}", userId, ex);
    // Check local cache first, otherwise allow the action
    Boolean cached = fraudCache.getIfPresent(userId);
    return cached != null ? cached : false;
}
```

I'd fail open here (allow the action) rather than fail closed (block everyone). If User Service goes down for 30
seconds, blocking all visits and likes is worse than letting a fraud user slip through temporarily. The local cache
still covers recently detected fraud users.

Config:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      user-service:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
```

---

## Eventual Consistency — fraud state propagation

In the monolith fraud detection is one atomic SQL statement. In microservices there's an unavoidable delay between
detection and propagation.

### How it flows

```
t=0ms   User does their 100th action
t=15ms  Interaction Service publishes UserActionEvent to Kafka
t=80ms  Fraud Service runs detection query, threshold reached
t=85ms  Fraud Service publishes FraudDetectedEvent
t=90ms  User Service updates is_fraud=true
t=95ms  Interaction Service updates local cache
t=100ms Next request from this user → 403
```

This timeline illustrates eventual consistency.

So there's roughly a ~100ms window where the user could sneak in one more action. I think that's acceptable - the
alternative is synchronous calls between services on every request which kills independence and adds latency.

### Local cache in Interaction Service

To avoid calling User Service on every visit/like, Interaction Service keeps a Caffeine cache of fraud statuses:

```java
private final Cache<Long, Boolean> fraudCache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(100_000)
        .build();

// On each visit/like — check cache first, fallback to User Service
public boolean isFraud(Long userId) {
    Boolean cached = fraudCache.getIfPresent(userId);
    if (cached != null) return cached;
    Boolean fresh = userServiceClient.isFraud(userId);
    fraudCache.put(userId, fresh);
    return Boolean.TRUE.equals(fresh);
}

// Kafka listener — instant cache update when fraud is detected
@KafkaListener(topics = "fraud-detected")
public void onFraudDetected(FraudDetectedEvent event) {
    fraudCache.put(event.userId(), true);
}
```

The 5-minute TTL is a backstop: even if a Kafka message is delayed or the consumer lags, the cache will eventually miss
and fetch fresh data from User Service.

### What if Kafka is down

If Kafka is unavailable when Interaction Service tries to publish `UserActionEvent` — the event is lost. The visit is
already committed to the DB but Fraud Service never sees it. That action won't count toward fraud detection.

A proper fix is the **Outbox pattern**: instead of publishing to Kafka directly, write the event to an `outbox` table in
the same transaction as the visit. A separate process then reads unpublished events and sends them to Kafka. This way
the visit and its event are atomic.
