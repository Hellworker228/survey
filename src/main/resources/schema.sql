CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_admin BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS surveys (
    id SERIAL PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    created_by INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    access_users TEXT DEFAULT '*',
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS questions (
    id SERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    survey_id INTEGER NOT NULL REFERENCES surveys (id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS options (
    id SERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    question_id INTEGER NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS responses (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    survey_id INTEGER NOT NULL REFERENCES surveys (id) ON DELETE CASCADE,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, survey_id)
);

CREATE TABLE IF NOT EXISTS answers (
    id SERIAL PRIMARY KEY,
    response_id INTEGER NOT NULL REFERENCES responses (id) ON DELETE CASCADE,
    question_id INTEGER NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    option_id INTEGER REFERENCES options (id) ON DELETE CASCADE,
    text TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE surveys ADD COLUMN IF NOT EXISTS ends_at TIMESTAMP;

ALTER TABLE questions ADD COLUMN IF NOT EXISTS correct_option_id INTEGER;
ALTER TABLE questions ADD COLUMN IF NOT EXISTS question_type VARCHAR(20);
ALTER TABLE questions ADD COLUMN IF NOT EXISTS is_required BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE questions
SET question_type = CASE
    WHEN EXISTS (SELECT 1 FROM options o WHERE o.question_id = questions.id) THEN 'SINGLE'
    ELSE 'TEXT'
END
WHERE question_type IS NULL;

ALTER TABLE questions ALTER COLUMN question_type SET DEFAULT 'SINGLE';
ALTER TABLE questions ALTER COLUMN question_type SET NOT NULL;

ALTER TABLE answers DROP CONSTRAINT IF EXISTS answers_response_id_question_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS answers_unique_idx ON answers (response_id, question_id, (COALESCE(option_id, 0)));

CREATE TABLE IF NOT EXISTS survey_access (
    survey_id INTEGER NOT NULL REFERENCES surveys (id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (survey_id, user_id)
);

INSERT INTO survey_access (survey_id, user_id)
SELECT s.id, u.id
FROM surveys s
JOIN users u ON (',' || REPLACE(s.access_users, ' ', '') || ',') LIKE ('%,' || u.id || ',%')
WHERE s.access_users IS NOT NULL AND TRIM(s.access_users) <> '' AND s.access_users NOT IN ('*', 'LIST')
ON CONFLICT DO NOTHING;

UPDATE surveys SET access_users = '*' WHERE access_users IS NULL OR TRIM(access_users) = '';

UPDATE surveys SET access_users = 'LIST' WHERE access_users NOT IN ('*', 'LIST');

CREATE INDEX IF NOT EXISTS idx_questions_survey ON questions (survey_id);
CREATE INDEX IF NOT EXISTS idx_options_question ON options (question_id);
CREATE INDEX IF NOT EXISTS idx_responses_survey ON responses (survey_id);
CREATE INDEX IF NOT EXISTS idx_answers_response ON answers (response_id);
CREATE INDEX IF NOT EXISTS idx_surveys_creator ON surveys (created_by);

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN;
UPDATE users SET email_verified = TRUE WHERE email_verified IS NULL;
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT FALSE;
ALTER TABLE users ALTER COLUMN email_verified SET NOT NULL;

CREATE TABLE IF NOT EXISTS email_tokens (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_tokens (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE surveys ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE surveys ADD COLUMN IF NOT EXISTS public_token VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS surveys_public_token_idx ON surveys (public_token);

ALTER TABLE responses ALTER COLUMN user_id DROP NOT NULL;

CREATE TABLE IF NOT EXISTS survey_participants (
    survey_id INTEGER NOT NULL REFERENCES surveys (id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (survey_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_email_tokens_user ON email_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_api_tokens_user ON api_tokens (user_id);
