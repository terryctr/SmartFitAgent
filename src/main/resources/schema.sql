-- SmartStudyAgent Database Schema
-- Supports both MySQL and H2 (in-memory) modes

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL DEFAULT '',
    grade VARCHAR(100) DEFAULT '',
    goal TEXT,
    weakness TEXT,
    preferred_style VARCHAR(200) DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS courses (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    subject VARCHAR(100),
    level VARCHAR(100),
    description TEXT,
    lessons INT DEFAULT 0,
    teacher VARCHAR(100),
    progress INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plans (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    subject VARCHAR(100),
    plan_date DATE,
    minutes INT DEFAULT 45,
    steps TEXT,
    status VARCHAR(100) DEFAULT '待开始',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notes (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    subject VARCHAR(100),
    content TEXT,
    tags VARCHAR(200),
    created_at VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS questions (
    id VARCHAR(64) PRIMARY KEY,
    subject VARCHAR(100),
    stem TEXT,
    option_a TEXT,
    option_b TEXT,
    option_c TEXT,
    option_d TEXT,
    answer VARCHAR(20),
    explanation TEXT
);

CREATE TABLE IF NOT EXISTS attempts (
    id VARCHAR(64) PRIMARY KEY,
    subject VARCHAR(100),
    score INT DEFAULT 0,
    total INT DEFAULT 100,
    mistakes TEXT,
    created_at VARCHAR(64)
);

-- New tables for extended functionality
CREATE TABLE IF NOT EXISTS workout_logs (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) DEFAULT 'u1001',
    workout_type VARCHAR(100) NOT NULL,
    exercise_name VARCHAR(200) NOT NULL,
    sets INT DEFAULT 1,
    reps INT DEFAULT 0,
    weight_kg DECIMAL(6,2) DEFAULT 0,
    duration_minutes INT DEFAULT 0,
    calories_burned INT DEFAULT 0,
    heart_rate_avg INT DEFAULT 0,
    notes TEXT,
    workout_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS body_measurements (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) DEFAULT 'u1001',
    weight_kg DECIMAL(5,2) NOT NULL,
    height_cm DECIMAL(5,2) DEFAULT 0,
    body_fat_percent DECIMAL(4,2) DEFAULT 0,
    muscle_mass_kg DECIMAL(5,2) DEFAULT 0,
    waist_cm DECIMAL(5,2) DEFAULT 0,
    chest_cm DECIMAL(5,2) DEFAULT 0,
    hip_cm DECIMAL(5,2) DEFAULT 0,
    bmi DECIMAL(4,2) DEFAULT 0,
    measurement_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS nutrition_logs (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) DEFAULT 'u1001',
    meal_type VARCHAR(50) NOT NULL,
    food_name VARCHAR(200) NOT NULL,
    quantity_grams DECIMAL(7,2) DEFAULT 100,
    calories INT DEFAULT 0,
    protein_g DECIMAL(6,2) DEFAULT 0,
    carbs_g DECIMAL(6,2) DEFAULT 0,
    fat_g DECIMAL(6,2) DEFAULT 0,
    fiber_g DECIMAL(6,2) DEFAULT 0,
    log_date DATE NOT NULL,
    meal_time TIME,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS challenges (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    challenge_type VARCHAR(100),
    target_value DECIMAL(10,2) DEFAULT 0,
    target_unit VARCHAR(50),
    start_date DATE,
    end_date DATE,
    status VARCHAR(50) DEFAULT '进行中',
    reward_points INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_challenges (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) DEFAULT 'u1001',
    challenge_id VARCHAR(64) NOT NULL,
    current_value DECIMAL(10,2) DEFAULT 0,
    completed BOOLEAN DEFAULT FALSE,
    completed_at TIMESTAMP,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS achievements (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) DEFAULT 'u1001',
    title VARCHAR(200) NOT NULL,
    description TEXT,
    badge_type VARCHAR(100),
    points_earned INT DEFAULT 0,
    earned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_conversations (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) DEFAULT 'u1001',
    agent_type VARCHAR(100) NOT NULL,
    title VARCHAR(200),
    model_used VARCHAR(100),
    total_tokens INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_messages (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    tokens_used INT DEFAULT 0,
    enrichment_applied TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_usage_stats (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) DEFAULT 'u1001',
    provider VARCHAR(100) NOT NULL,
    model VARCHAR(100),
    total_requests INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    total_cost_usd DECIMAL(10,6) DEFAULT 0,
    stat_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pomodoro_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) DEFAULT 'u1001',
    task_name VARCHAR(200),
    duration_minutes INT DEFAULT 25,
    completed BOOLEAN DEFAULT FALSE,
    session_date DATE NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vocabulary_items (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) DEFAULT 'u1001',
    word VARCHAR(200) NOT NULL,
    translation VARCHAR(500),
    example_sentence TEXT,
    category VARCHAR(100),
    mastery_level INT DEFAULT 0,
    next_review DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
