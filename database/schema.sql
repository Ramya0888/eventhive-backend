
-- EVENTHIVE DATABASE SCHEMA


CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(20) NOT NULL UNIQUE
);


INSERT IGNORE INTO roles (role_name) VALUES ('ADMIN'), ('ORGANIZER'), ('ATTENDEE');


CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

   
    INDEX idx_users_email (email)
);


CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    PRIMARY KEY (user_id, role_id),  -- Composite Key

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- ------------------------------------------------------------
-- TABLE: venues  (separate table, NOT columns on events — see normalization note)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS venues (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(150) NOT NULL,
    address        VARCHAR(255) NOT NULL,
    city           VARCHAR(100) NOT NULL,
    total_capacity INT NOT NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------
-- TABLE: categories  (lookup table, like roles)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

INSERT IGNORE INTO categories (name) VALUES
    ('Music'), ('Technology'), ('Business'), ('Sports'), ('Arts'), ('Food');

-- ------------------------------------------------------------
-- TABLE: events
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    event_date      DATE NOT NULL,
    start_time      TIME,
    end_time        TIME,
    banner_url      VARCHAR(500),
    available_seats INT,
    average_rating  DOUBLE,
    organizer_id    BIGINT NOT NULL,
    category_id     BIGINT,
    venue_id        BIGINT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_events_organizer FOREIGN KEY (organizer_id) REFERENCES users(id),
    CONSTRAINT fk_events_category  FOREIGN KEY (category_id)  REFERENCES categories(id),
    CONSTRAINT fk_events_venue     FOREIGN KEY (venue_id)     REFERENCES venues(id),

    -- Composite index: supports "upcoming PUBLISHED events" queries (Day 3 search).
    INDEX idx_events_status_date (status, event_date)
);

-- NOTE: Run this once on a fresh database. Unlike the CREATE TABLE IF NOT EXISTS
-- statements above, this ALTER is not idempotent — re-running it on a DB that
-- already has the index will error with "Duplicate key name". Skip it if the
-- index already exists (check: SHOW INDEX FROM events WHERE Key_name='idx_events_search').
ALTER TABLE events ADD FULLTEXT INDEX idx_events_search (title, description);

-- ------------------------------------------------------------
-- TABLE: seat_categories  (pricing tiers per event)
-- Each event can have multiple pricing tiers (VIP, Premium, General).
-- Separate from the seats table so pricing is defined once per tier,
-- not repeated on every seat row.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS seat_categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id    BIGINT NOT NULL,
    name        VARCHAR(50) NOT NULL,       -- e.g. VIP, Premium, General
    price       DECIMAL(10, 2) NOT NULL,
    total_count INT NOT NULL,               -- how many seats in this tier
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_seat_categories_event
        FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE
);

-- ------------------------------------------------------------
-- TABLE: seats  (individual bookable seats)
-- One row per physical seat. Status tracks availability in real time.
-- The critical index is (event_id, status) — the booking query
-- "find available seats for this event" hits this constantly.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS seats (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id        BIGINT NOT NULL,
    seat_category_id BIGINT NOT NULL,
    seat_number     VARCHAR(20) NOT NULL,   -- e.g. "VIP-001", "GEN-042"
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- The index Module 4 depends on: fast lookup of available seats per event.
    INDEX idx_seats_event_status (event_id, status),

    CONSTRAINT fk_seats_event
        FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_seats_category
        FOREIGN KEY (seat_category_id) REFERENCES seat_categories(id)
);


CREATE TABLE IF NOT EXISTS bookings (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_reference VARCHAR(20) NOT NULL UNIQUE,
    user_id           BIGINT NOT NULL,
    event_id          BIGINT NOT NULL,
    total_amount      DECIMAL(10, 2) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reserved_at       TIMESTAMP,
    confirmed_at      TIMESTAMP,
    expires_at        TIMESTAMP,             -- when the reservation times out
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Fast lookup by reference code (QR scan, customer support).
    INDEX idx_bookings_reference (booking_reference),
    -- Fast lookup of pending bookings for the cleanup job.
    INDEX idx_bookings_status_expires (status, expires_at),

    CONSTRAINT fk_bookings_user  FOREIGN KEY (user_id)  REFERENCES users(id),
    CONSTRAINT fk_bookings_event FOREIGN KEY (event_id) REFERENCES events(id)
);


CREATE TABLE IF NOT EXISTS booking_items (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id        BIGINT NOT NULL,
    seat_id           BIGINT NOT NULL,
    seat_category_id  BIGINT NOT NULL,
    price_at_booking  DECIMAL(10, 2) NOT NULL,

    CONSTRAINT fk_booking_items_booking  FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_booking_items_seat     FOREIGN KEY (seat_id)    REFERENCES seats(id),
    CONSTRAINT fk_booking_items_category FOREIGN KEY (seat_category_id) REFERENCES seat_categories(id)
);


-- TABLE: payments
-- One payment record per successful Razorpay transaction.
-- Stores Razorpay ids for audit and dispute resolution.

CREATE TABLE IF NOT EXISTS payments (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id            BIGINT NOT NULL UNIQUE,  -- one payment per booking
    razorpay_order_id     VARCHAR(100) NOT NULL,
    razorpay_payment_id   VARCHAR(100),            -- null until payment succeeds
    razorpay_signature    VARCHAR(300),            -- null until verified
    amount                DECIMAL(10, 2) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_payments_order_id (razorpay_order_id),

    CONSTRAINT fk_payments_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(id)
);


-- ============================================================
-- VIEW: v_event_summary
-- Joins events with venues, booking counts, and revenue.
-- Used by both organizer and admin dashboards.
 
-- ============================================================
CREATE OR REPLACE VIEW v_event_summary AS
SELECT
    e.id                                    AS event_id,
    e.title                                 AS event_title,
    e.status                                AS event_status,
    e.event_date,
    e.available_seats,
    u.id                                    AS organizer_id,
    u.name                                  AS organizer_name,
    v.city                                  AS venue_city,
    c.name                                  AS category_name,
    COUNT(DISTINCT b.id)                    AS total_bookings,
    COALESCE(SUM(p.amount), 0)              AS total_revenue
FROM events e
JOIN users u        ON u.id = e.organizer_id
LEFT JOIN venues v  ON v.id = e.venue_id
LEFT JOIN categories c ON c.id = e.category_id
LEFT JOIN bookings b
    ON b.event_id = e.id
    AND b.status IN ('CONFIRMED')
LEFT JOIN payments p
    ON p.booking_id = b.id
    AND p.status = 'SUCCESS'
GROUP BY
    e.id, e.title, e.status, e.event_date,
    e.available_seats, u.id, u.name,
    v.city, c.name;


-- ============================================================
-- STORED PROCEDURE: sp_organizer_revenue
-- Returns revenue breakdown for a specific organizer
-- between two dates.

-- ============================================================
DELIMITER //
CREATE PROCEDURE sp_organizer_revenue(
    IN  p_organizer_id BIGINT,
    IN  p_from_date    DATE,
    IN  p_to_date      DATE
)
BEGIN
    SELECT
        e.id                            AS event_id,
        e.title                         AS event_title,
        e.event_date,
        COUNT(DISTINCT b.id)            AS confirmed_bookings,
        COALESCE(SUM(p.amount), 0)      AS revenue,
        RANK() OVER (
            ORDER BY COALESCE(SUM(p.amount), 0) DESC
        )                               AS revenue_rank
    FROM events e
    LEFT JOIN bookings b
        ON b.event_id = e.id
        AND b.status = 'CONFIRMED'
    LEFT JOIN payments p
        ON p.booking_id = b.id
        AND p.status = 'SUCCESS'
    WHERE e.organizer_id = p_organizer_id
      AND e.event_date BETWEEN p_from_date AND p_to_date   -- moved here
    GROUP BY e.id, e.title, e.event_date
    ORDER BY revenue DESC;
END //
DELIMITER ;



CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name  VARCHAR(50),
    record_id   BIGINT,
    action      VARCHAR(20),
    old_value   VARCHAR(100),
    new_value   VARCHAR(100),
    changed_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DROP TRIGGER IF EXISTS trg_booking_status_change;

DELIMITER $$

CREATE TRIGGER trg_booking_status_change
AFTER UPDATE ON bookings
FOR EACH ROW
BEGIN
    IF OLD.status != NEW.status THEN
        INSERT INTO audit_logs (table_name, record_id, action, old_value, new_value)
        VALUES ('bookings', NEW.id, 'STATUS_CHANGE', OLD.status, NEW.status);
    END IF;
END$$

DELIMITER ;

--//for scanning and verification 
ALTER TABLE bookings 
ADD COLUMN checked_in BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN checked_in_at TIMESTAMP NULL;


-- ------------------------------------------------------------
-- TABLE: feedback
-- Attendee submits text feedback after attending an event.
-- Gemini analyzes it and classifies as POSITIVE/NEUTRAL/NEGATIVE.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS feedback (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    event_id         BIGINT NOT NULL,
    content          TEXT NOT NULL,
    sentiment        VARCHAR(20),        -- POSITIVE, NEUTRAL, NEGATIVE
    confidence_score DOUBLE,             -- 0.0 to 1.0
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Index for analytics: "show me all feedback for event X by sentiment"
    INDEX idx_feedback_event_sentiment (event_id, sentiment),

    CONSTRAINT fk_feedback_user  FOREIGN KEY (user_id)  REFERENCES users(id),
    CONSTRAINT fk_feedback_event FOREIGN KEY (event_id) REFERENCES events(id)
);