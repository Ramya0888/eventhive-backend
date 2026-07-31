# EventHive Backend

> Production-grade REST API for an event management and ticketing platform.
> Built with Spring Boot 3.x, Java 21, and MySQL 8.

**Live API:** https://eventhive-backend-rx7z.onrender.com  
**Frontend:** https://eventhive-frontend-plum.vercel.app

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5, Spring Security 6 |
| Database | MySQL 8.4 (Aiven) |
| ORM | Hibernate / Spring Data JPA |
| Auth | JWT (JJWT 0.12.x), BCrypt |
| Payments | Razorpay |
| Email | Brevo SMTP + JavaMailSender |
| AI | Google Gemini API (RestTemplate) |
| PDF | iText 5 |
| QR Code | ZXing |
| Deployment | Docker → Render |
| Build | Maven (mvnw wrapper) |

---

## Architecture

```
┌─────────────────┐     HTTPS      ┌──────────────────────┐
│  React Frontend │ ─────────────► │  Spring Boot Backend  │
│   (Vercel)      │                │  (Render / Docker)    │
└─────────────────┘                └──────────┬───────────┘
                                              │ JDBC
                                   ┌──────────▼───────────┐
                                   │   MySQL 8.4 (Aiven)  │
                                   └──────────────────────┘
```

---

## Key Features & Technical Decisions

### JWT Authentication with Role-Based Access Control
- Stateless JWT auth with access tokens (15 min) and refresh tokens (7 days)
- Custom `type` claim prevents access tokens from being replayed at the refresh endpoint
- Roles: `ADMIN`, `ORGANIZER`, `ATTENDEE`

### Concurrent Seat Booking
- `@Transactional(isolation = Isolation.SERIALIZABLE)` — strictest isolation level, prevents phantom reads
- `@Lock(LockModeType.PESSIMISTIC_WRITE)` on seat fetch — database-level row locking
- Consistent seat ID sort ordering before locking — prevents deadlocks
- 10-minute reservation window with `@Scheduled` cleanup job releasing expired bookings

### Payment Integration
- Three-step Razorpay flow: create order → checkout popup → verify signature
- HMAC-SHA256 signature verification prevents fake payment confirmations
- Duplicate order protection — reuses existing `CREATED` payment record on retry

### AI Integration (Google Gemini API)
- Event description generation via `POST /api/ai/generate-description`
- Feedback sentiment analysis — classifies as `POSITIVE/NEUTRAL/NEGATIVE` with confidence score
- `@Async` sentiment analysis — API responds immediately, Gemini runs in background thread
- Separate `TicketDataLoader` bean resolves Spring AOP self-invocation problem

### QR Ticket + PDF Generation
- ZXing generates QR code encoding the booking reference
- iText 5 generates PDF with event details, seat list, and embedded QR
- `ByteArrayResource` — entire flow in memory, no temp files written to disk
- `@Async` delivery — payment endpoint responds in milliseconds, email sends in background

### Analytics with Advanced SQL
- `LAG()` window function — month-over-month revenue growth calculation
- `RANK()` window function — top organizers by revenue
- `SUM() OVER` — running revenue total per organizer
- CTE (`WITH event_revenue AS (...)`) for readable multi-step aggregations
- Stored procedure `sp_organizer_revenue` for date-range revenue reports
- Database view `v_event_summary` encapsulating multi-table join

### Security — IDOR Prevention
- Non-owner access to private resources returns `404`, not `403`
- 404 prevents attackers from enumerating valid resource IDs
- All organizer endpoints verify JWT principal matches resource owner

---

## Project Structure

```
src/main/java/com/eventhive/eventhive_backend/
├── config/
│   ├── CorsConfig.java
│   ├── DataSeeder.java          # Seeds roles, categories, admin on startup
│   └── SecurityConfig.java
├── controller/                  # REST endpoints
├── dto/                         # Request/Response objects
├── entity/                      # JPA entities
├── enums/                       # EventStatus, BookingStatus, SeatStatus, PaymentStatus
├── exception/                   # Custom exceptions + GlobalExceptionHandler
├── repository/                  # Spring Data JPA repositories
├── security/                    # JWT filter, JwtUtil, CustomUserDetails
└── service/                     # Business logic
    ├── AIService.java
    ├── AuthService.java
    ├── BookingService.java
    ├── EventService.java
    ├── FeedbackService.java
    ├── PaymentService.java
    ├── TicketDataLoader.java    # Separate bean for @Transactional data loading
    └── TicketService.java
```

---

## API Endpoints

### Auth
```
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
```

### Events
```
GET    /api/events                    # Paginated catalog (public)
GET    /api/events/{id}               # Single event
POST   /api/events                    # Create (ORGANIZER)
PATCH  /api/events/{id}/submit        # Submit for approval (ORGANIZER)
PATCH  /api/events/{id}/approve       # Approve (ADMIN)
PATCH  /api/events/{id}/reject        # Reject (ADMIN)
PATCH  /api/events/{id}/cancel        # Cancel (ORGANIZER)
GET    /api/events/my-events          # Organizer's own events
GET    /api/events/pending-approval   # Admin queue
```

### Bookings
```
POST /api/bookings                    # Create booking (ATTENDEE)
GET  /api/bookings/my-bookings        # My bookings (ATTENDEE)
POST /api/bookings/{id}/cancel        # Cancel booking
POST /api/bookings/scan               # QR scan check-in (ORGANIZER)
```

### Payments
```
POST /api/payments/create-order       # Create Razorpay order
POST /api/payments/verify             # Verify payment signature
```

### AI
```
POST /api/ai/generate-description     # Generate event description (ORGANIZER)
```

### Feedback
```
POST /api/feedback                              # Submit feedback (ATTENDEE)
GET  /api/feedback/event/{eventId}              # Get event feedback (ORGANIZER)
GET  /api/feedback/event/{eventId}/sentiment    # Sentiment breakdown (ORGANIZER)
```

### Analytics
```
GET /api/analytics/organizer          # Organizer dashboard data
GET /api/analytics/admin              # Admin dashboard data
```

---

## Running Locally

### Prerequisites
- Java 21
- MySQL 8.x running locally
- Maven (or use `mvnw`)

### Setup

```bash
# Clone the repo
git clone https://github.com/Ramya0888/eventhive-backend.git
cd eventhive-backend

# Create local properties (gitignored — never committed)
cp src/main/resources/application.properties src/main/resources/application-local.properties
```

Edit `application-local.properties` with your local values:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/eventhive_db
spring.datasource.username=root
spring.datasource.password=yourpassword
jwt.secret=YourLocalSecretAtLeast32CharactersLong
razorpay.key.id=rzp_test_xxxxx
razorpay.key.secret=yoursecret
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=youremail@gmail.com
spring.mail.password=your_app_password
gemini.api.key=your_gemini_key
mail.from.address=youremail@gmail.com
```

```bash
# Run
./mvnw spring-boot:run
```

The `DataSeeder` automatically creates:
- Roles: `ADMIN`, `ORGANIZER`, `ATTENDEE`
- Categories: Music, Technology, Business, Sports, Arts, Food
- Default admin: `admin@eventhive.com`

### Running Tests

```bash
./mvnw test
```

Tests cover: JWT token type isolation, booking concurrency, password hashing, sentiment parsing, and IDOR prevention. No database or Spring context required — all mocked with Mockito.

---

## Deployment

Deployed on **Render** using Docker. All secrets are environment variables — never committed.

```dockerfile
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/eventhive-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Environment variables required on Render:**
```
DB_URL, DB_USERNAME, DB_PASSWORD
JWT_SECRET
RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET
MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM
GEMINI_API_KEY
SHOW_SQL=false
SPRING_PROFILES_ACTIVE=production
```

---

## Notable Engineering Decisions

| Decision | Reason |
|---|---|
| SERIALIZABLE isolation for booking | Prevents phantom reads — a REPEATABLE_READ gap could allow double-booking |
| Pessimistic locking on seats | Database-level guarantee, not application-level race condition |
| Sorted seat ID locking order | Consistent ordering prevents deadlock between concurrent transactions |
| Separate `TicketDataLoader` bean | Spring AOP self-invocation bypasses `@Transactional` proxy — separate bean fixes this |
| `type` claim in JWT | Prevents access token replay at the refresh endpoint |
| 404 instead of 403 for IDOR | Non-disclosure — don't reveal resource existence to non-owners |
| `@Async` for ticket generation | Payment endpoint returns in milliseconds; PDF+email runs in background |
| Date-based feedback validation | Handles scheduler lag — checks actual event date, not just status enum |
| `ByteArrayResource` for PDF | No temp files written to disk — entire PDF lives in memory |
