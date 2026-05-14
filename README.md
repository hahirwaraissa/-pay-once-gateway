# Idempotency-Gateway (The "Pay-Once" Protocol)

Professional middleware service built for **IgirePay Technologies** to ensure payment requests are processed exactly once.

## 🚀 Overview

The **Idempotency-Gateway** acts as a safety layer for payment processing. It prevents duplicate charges by tracking unique `Idempotency-Key` headers and caching their responses. If a request is retried with the same key, the gateway returns the previous result instead of re-processing the payment.

### Key Features
- **Deterministic Idempotency:** Uses SHA-256 hashing to verify that retried requests have the exact same payload.
- **Race Condition Protection:** Handles concurrent "in-flight" requests using a locking mechanism (status tracking).
- **Key Expiration:** Automatically expires and cleans up idempotency keys after 24 hours (Developer's Choice).
- **Secure API:** Basic API Key authentication for all endpoints.
- **Transparent Caching:** Includes `X-Cache-Hit: true` header for cached responses.

---

## 🛠️ Tech Stack
- **Java 17**
- **Spring Boot 3.2.5**
- **Spring Data JPA**
- **H2 Database** (In-memory)
- **Lombok** (Clean code)
- **JUnit 5 & MockMvc** (Testing)

---

## 🏁 Getting Started

### Prerequisites
- JDK 17
- Maven (included via `mvnw`)

### Running the Project
1. Clone the repository.
2. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```
3. Access the H2 Console (optional): `http://localhost:8080/h2-console`
   - **JDBC URL:** `jdbc:h2:mem:pay_once_db`
   - **User:** `sa`
   - **Password:** (blank)

---

## 📖 API Documentation

### Process Payment
`POST /process-payment`

**Headers:**
- `X-API-KEY`: `she-can-code-2024`
- `Idempotency-Key`: `[UNIQUE_UUID]`

**Request Body:**
```json
{
  "targetAccount": "ACC-556677",
  "amount": 150.50,
  "currency": "USD",
  "description": "Invoice #1234"
}
```

**Responses:**
- `200 OK`: Payment processed or retrieved from cache.
- `400 Bad Request`: Missing/Invalid fields or missing headers.
- `401 Unauthorized`: Missing or invalid API Key.
- `409 Conflict`: Request already in-progress.
- `422 Unprocessable Entity`: Key exists but payload mismatch.

---

## 🧪 Testing

The project includes comprehensive integration tests covering:
- ✅ Success flow (First-time request)
- ✅ Cache hit (Duplicate request)
- ✅ Payload mismatch validation
- ✅ In-progress request handling (Conflict)
- ✅ API Key security

Run tests using:
```bash
./mvnw test
```

---

## 📐 Design Decisions

1. **Payload Hashing:** We store a SHA-256 hash of the request body. If a user sends the same key but different data, we reject it as a conflict.
2. **State Management:** Records transition from `IN_PROGRESS` to `COMPLETED`. Concurrent requests with the same key will receive a `409 Conflict` until the first one finished.
3. **Key Expiration:** To prevent the database from growing infinitely, keys are valid for 24 hours. After this, the key can be reused for a new transaction.
