-- ==============================================================================
-- SCHEMA DDL PARA H2 (Modo PostgreSQL)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255),
    auth_user_id UUID UNIQUE,
    role VARCHAR(10) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    profile_picture VARCHAR(255),
    best_time_ms BIGINT CHECK (best_time_ms > 0),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS categories (
    category_id UUID PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS threads (
    thread_id UUID PRIMARY KEY,
    category_id UUID NOT NULL,
    user_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_threads_category FOREIGN KEY (category_id) REFERENCES categories(category_id),
    CONSTRAINT fk_threads_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS posts (
    post_id UUID PRIMARY KEY,
    thread_id UUID NOT NULL,
    user_id UUID NOT NULL,
    parent_post_id UUID,
    content TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_posts_thread FOREIGN KEY (thread_id) REFERENCES threads(thread_id),
    CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_posts_parent FOREIGN KEY (parent_post_id) REFERENCES posts(post_id)
);

CREATE TABLE IF NOT EXISTS private_conversations (
    conversation_id UUID PRIMARY KEY,
    participant_one_id UUID NOT NULL,
    participant_two_id UUID NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_private_conversation_distinct CHECK (participant_one_id <> participant_two_id),
    CONSTRAINT uq_private_conversation_participants UNIQUE (participant_one_id, participant_two_id),
    CONSTRAINT fk_private_conversation_one FOREIGN KEY (participant_one_id) REFERENCES users(user_id),
    CONSTRAINT fk_private_conversation_two FOREIGN KEY (participant_two_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS private_messages (
    message_id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    content VARCHAR(2000) NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_private_message_conversation FOREIGN KEY (conversation_id) REFERENCES private_conversations(conversation_id),
    CONSTRAINT fk_private_message_sender FOREIGN KEY (sender_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS post_reports (
    report_id UUID PRIMARY KEY, post_id UUID NOT NULL, reporter_id UUID NOT NULL, reason VARCHAR(500) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING', is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE, reviewed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_report_post FOREIGN KEY (post_id) REFERENCES posts(post_id),
    CONSTRAINT fk_reporter FOREIGN KEY (reporter_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS achievements (
    ach_id UUID PRIMARY KEY,
    ach_name VARCHAR(100) NOT NULL UNIQUE,
    ach_description VARCHAR(500) NOT NULL,
    ach_picture VARCHAR(255),
    is_ach_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    target_value INT,
    progress_label VARCHAR(50),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS user_achievements (
    user_id UUID NOT NULL,
    ach_id UUID NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    is_unlocked BOOLEAN NOT NULL DEFAULT FALSE,
    unlocked_at TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (user_id, ach_id),
    CONSTRAINT fk_user_ach_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_user_ach_ach FOREIGN KEY (ach_id) REFERENCES achievements(ach_id)
);


CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_id UUID PRIMARY KEY,
    auth_sub VARCHAR(64) NOT NULL,
    idem_key VARCHAR(128) NOT NULL,
    request_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(512) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(12) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    response_status INT,
    response_body TEXT,
    response_content_type VARCHAR(255),
    location_header VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_idempotency_keys_owner_key UNIQUE (auth_sub, idem_key)
);
CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);
