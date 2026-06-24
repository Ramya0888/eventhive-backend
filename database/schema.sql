
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