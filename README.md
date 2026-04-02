# Social Network Backend

## Overview

This project implements a high-performance backend system for a social network with:

* Profile visits & likes tracking
* Fraud detection
* Bulk data processing
* Optimized SQL-based persistence (no JPA/Hibernate)

---

## Architecture Overview

```
┌─────────────────────────────────────────────┐
│               Spring Boot App               │
│                                             │
│  Controllers → Services → Repositories      │
│                    ↓                        │
│             FraudService (@Async)           │
└─────────────────┬───────────────────────────┘
                  │
         ┌────────▼────────┐
         │   PostgreSQL    │
         │  users          │
         │  profile_visits │
         │  profile_likes  │
         └─────────────────┘
```

The application is a **monolith** structured around three bounded contexts that map cleanly to a future microservices
split:

| Context         | Responsibility                           |
|-----------------|------------------------------------------|
| User Management | Profile creation, metadata, fraud status |
| Interaction     | Recording visits and likes               |
| Fraud Detection | Async analysis, marking fraudulent users |

___

## Tech Stack

* Java 21
* Spring Boot 3.5
* Spring Data JDBC / JdbcTemplate
* PostgreSQL
* Liquibase
* Docker

---

## Key Design Decisions

### 1. No Hibernate / JPA

To ensure:

* predictable SQL
* high performance
* no hidden N+1 issues

---

### 2. Atomic Fraud Detection

Fraud detection is implemented using a **single atomic SQL UPDATE**:

* eliminates race conditions
* guarantees consistency
* avoids distributed locks

---

### 3. Database-Driven Logic

Critical computations (fraud detection, aggregations) are executed in SQL:

* reduces network overhead
* leverages DB optimizations
* ensures ACID guarantees

---

### 4. Async Processing

Fraud detection runs asynchronously:

* API remains fast
* system scales better under load

---

## Fraud Detection Logic

A user is marked as fraud if:

> They perform 100+ actions (visits + likes) within 10 minutes from their first action.

Implemented using:

* CTE (WITH)
* aggregation
* atomic UPDATE

---

## Database Schema

```sql
users
id          BIGSERIAL PRIMARY KEY
  username    VARCHAR(50)  NOT NULL UNIQUE
  full_name   VARCHAR(100) NOT NULL
  age         INT
  metadata    JSONB                        -- dynamic user-defined fields
  is_fraud    BOOLEAN NOT NULL DEFAULT false
  created_at  TIMESTAMPTZ DEFAULT NOW()

profile_visits
  id          BIGSERIAL PRIMARY KEY
  visitor_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE
CASCADE
  visited_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE
CASCADE
  visited_at  TIMESTAMPTZ DEFAULT NOW()

profile_likes
  id           BIGSERIAL PRIMARY KEY
  user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE
CASCADE
  liked_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE
CASCADE
  liked_at     TIMESTAMPTZ DEFAULT NOW()
  UNIQUE (user_id, liked_user_id)
```

**Indexes:**

| Index                     | Columns                    | Purpose         |
|---------------------------|----------------------------|-----------------|
| `idx_visits_visited_time` | `(visited_id, visited_at)` | Visitors query  |
| `idx_visits_visitor_time` | `(visitor_id, visited_at)` | Fraud detection |
| `idx_likes_user_time`     | `(user_id, liked_at)`      | Fraud detection |
| `idx_users_created_at`    | `(created_at)`             | General queries |

---

## API Endpoints

### User API

| Method | Path             | Description           |
|--------|------------------|-----------------------|
| `POST` | `/user`          | Create a user profile |
| `GET`  | `/user/{userId}` | Get a user profile    |

**POST /user**

```json
{
  "username": "john_doe",
  "fullName": "John Doe",
  "age": 28,
  "metadata": {
    "city": "Paris",
    "interests": [
      "music",
      "travel"
    ]
  }
}
```

Response `201 Created`:

```json
{
  "id": 42
}
```

### Visit API

| Method | Path                      | Description                   |
|--------|---------------------------|-------------------------------|
| `POST` | `/user/visit`             | Record a profile visit        |
| `POST` | `/user/visit/bulk`        | Bulk insert visits (max 1000) |
| `GET`  | `/user/{userId}/visitors` | Get visitors of a profile     |

**GET /user/{userId}/visitors**

Query params: `page` (default 0), `size` (default 50, max 200)

Response `200 OK`:

```json
[
  {
    "visitorId": 7,
    "lastVisit": "2026-03-15T14:32:00Z"
  },
  {
    "visitorId": 3,
    "lastVisit": "2026-03-15T12:10:00Z"
  }
]
```

Sorted by most recent visit descending.

---

### Like API

| Method | Path              | Description                  |
|--------|-------------------|------------------------------|
| `POST` | `/user/like`      | Like a user profile          |
| `POST` | `/user/like/bulk` | Bulk insert likes (max 1000) |

Liking the same user twice is idempotent — returns `201` both times, stored once.

---

## Bulk Insert

Implemented using:

* JdbcTemplate batchUpdate
* optimized for large datasets

---

## Run Locally

```bash
docker-compose up
./gradlew bootRun
```

---

## Testing

```bash
# Unit + integration tests (requires Docker for Testcontainers)
./gradlew test

# Run only integration tests
./gradlew test --tests "*.integration.*"
```

---

## Microservices architecture & state propagationng

yoy can find it there:

```bash
./docs/microservices-architecture.md
```