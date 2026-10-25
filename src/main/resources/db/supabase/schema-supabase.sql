-- ==============================================================================
-- SCHEMA DEFINITION FOR SUPABASE (POSTGRESQL)
-- Project: PWA Videojuego (Comunidad y Lore)
-- Description: Creación de tablas, constraints, auditoría, índices y triggers.
-- ==============================================================================

-- 1. Extensiones necesarias para UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 2. Función Trigger para actualizar automáticamente 'updated_at'
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ==============================================================================
-- TABLA: users
-- ==============================================================================
CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255),
    -- Plain local PostgreSQL does not include the Supabase auth schema.
    auth_user_id UUID UNIQUE,
    role VARCHAR(10) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    profile_picture VARCHAR(255),
    
    -- Audit details
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

-- ==============================================================================
-- TABLA: categories
-- ==============================================================================
CREATE TABLE IF NOT EXISTS categories (
    category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    
    -- Audit details
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

-- ==============================================================================
-- TABLA: threads (Hilos del foro)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS threads (
    thread_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES categories(category_id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Audit details
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

-- ==============================================================================
-- TABLA: posts (Publicaciones y respuestas en hilos)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS posts (
    post_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id UUID NOT NULL REFERENCES threads(thread_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    parent_post_id UUID REFERENCES posts(post_id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    
    -- Audit details
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

-- ==============================================================================
-- TABLA: achievements (Logros del sistema)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS achievements (
    ach_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ach_name VARCHAR(100) NOT NULL UNIQUE,
    ach_description VARCHAR(500) NOT NULL,
    ach_picture VARCHAR(255),
    is_ach_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Audit details
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

-- ==============================================================================
-- TABLA: user_achievements (Progreso y logros desbloqueados por usuario)
-- Nota: En el diagrama aparece como 'user_achivements'
-- ==============================================================================
CREATE TABLE IF NOT EXISTS user_achievements (
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    ach_id UUID NOT NULL REFERENCES achievements(ach_id) ON DELETE CASCADE,
    progress INT NOT NULL DEFAULT 0,
    is_unlocked BOOLEAN NOT NULL DEFAULT FALSE,
    unlocked_at TIMESTAMPTZ,
    
    -- Audit details
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    
    PRIMARY KEY (user_id, ach_id)
);

-- ==============================================================================
-- ÍNDICES PARA ALTO RENDIMIENTO (Foreign keys y búsquedas habituales)
-- ==============================================================================
CREATE INDEX IF NOT EXISTS idx_threads_category_id ON threads(category_id);
CREATE INDEX IF NOT EXISTS idx_threads_user_id ON threads(user_id);
CREATE INDEX IF NOT EXISTS idx_threads_created_at ON threads(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_posts_thread_id ON posts(thread_id);
CREATE INDEX IF NOT EXISTS idx_posts_user_id ON posts(user_id);
CREATE INDEX IF NOT EXISTS idx_posts_parent_post_id ON posts(parent_post_id);

CREATE INDEX IF NOT EXISTS idx_user_achievements_user ON user_achievements(user_id);
CREATE INDEX IF NOT EXISTS idx_user_achievements_ach ON user_achievements(ach_id);

-- ==============================================================================
-- TRIGGERS PARA ACTUALIZACIÓN AUTOMÁTICA DE 'updated_at'
-- ==============================================================================
DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_categories_updated_at ON categories;
CREATE TRIGGER trg_categories_updated_at
BEFORE UPDATE ON categories
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_threads_updated_at ON threads;
CREATE TRIGGER trg_threads_updated_at
BEFORE UPDATE ON threads
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_posts_updated_at ON posts;
CREATE TRIGGER trg_posts_updated_at
BEFORE UPDATE ON posts
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_achievements_updated_at ON achievements;
CREATE TRIGGER trg_achievements_updated_at
BEFORE UPDATE ON achievements
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_user_achievements_updated_at ON user_achievements;
CREATE TRIGGER trg_user_achievements_updated_at
BEFORE UPDATE ON user_achievements
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==============================================================================
-- SUPABASE ROW LEVEL SECURITY (RLS) - OPCIONAL PERO RECOMENDADO
-- ==============================================================================
-- ALTER TABLE users ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE categories ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE threads ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE posts ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE achievements ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE user_achievements ENABLE ROW LEVEL SECURITY;

