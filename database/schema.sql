
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