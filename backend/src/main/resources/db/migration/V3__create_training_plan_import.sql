CREATE TABLE workout_plan (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), external_id VARCHAR(120) NOT NULL,
 name VARCHAR(180) NOT NULL, version INTEGER NOT NULL, status VARCHAR(30) NOT NULL,
 valid_from DATE, valid_until DATE, activated_at TIMESTAMPTZ, superseded_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_workout_plan_version UNIQUE(user_id, external_id, version),
 CONSTRAINT ck_workout_plan_status CHECK(status IN ('DRAFT','PENDING_VALIDATION','ACTIVE','SUPERSEDED','ARCHIVED','CANCELLED'))
);
CREATE INDEX idx_workout_plan_user_status ON workout_plan(user_id,status);

CREATE TABLE workout_plan_day (
 id UUID PRIMARY KEY, workout_plan_id UUID NOT NULL REFERENCES workout_plan(id) ON DELETE CASCADE,
 week_number INTEGER NOT NULL, day_number INTEGER NOT NULL, day_name VARCHAR(80), session_name VARCHAR(160) NOT NULL, day_order INTEGER NOT NULL,
 CONSTRAINT uk_plan_day UNIQUE(workout_plan_id,week_number,day_number)
);
CREATE TABLE exercise (
 id UUID PRIMARY KEY, code VARCHAR(80) NOT NULL UNIQUE, name VARCHAR(180) NOT NULL, muscle_group VARCHAR(100),
 equipment VARCHAR(100), active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE planned_exercise (
 id UUID PRIMARY KEY, workout_plan_day_id UUID NOT NULL REFERENCES workout_plan_day(id) ON DELETE CASCADE,
 exercise_id UUID NOT NULL REFERENCES exercise(id), exercise_order INTEGER NOT NULL, sets INTEGER NOT NULL,
 reps_min INTEGER NOT NULL, reps_max INTEGER NOT NULL, target_rir NUMERIC(4,1), target_rpe NUMERIC(4,1),
 rest_seconds INTEGER, tempo VARCHAR(30), warmup_required BOOLEAN NOT NULL DEFAULT FALSE,
 superset_group VARCHAR(40), alternative_exercise_code VARCHAR(80), instructions TEXT, notes TEXT,
 CONSTRAINT uk_planned_exercise_order UNIQUE(workout_plan_day_id,exercise_order)
);
CREATE TABLE import_job (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), import_type VARCHAR(50) NOT NULL,
 schema_version VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL, original_filename VARCHAR(255),
 checksum VARCHAR(64) NOT NULL, file_size BIGINT NOT NULL, content TEXT NOT NULL, preview_json TEXT,
 external_id VARCHAR(120), plan_version INTEGER, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 expires_at TIMESTAMPTZ NOT NULL, confirmed_at TIMESTAMPTZ, plan_id UUID REFERENCES workout_plan(id),
 CONSTRAINT uk_confirmed_import UNIQUE(user_id,import_type,external_id,plan_version,checksum)
);
CREATE TABLE import_error (
 id UUID PRIMARY KEY, import_job_id UUID NOT NULL REFERENCES import_job(id) ON DELETE CASCADE,
 row_number INTEGER, column_name VARCHAR(100), error_code VARCHAR(80) NOT NULL, message VARCHAR(500) NOT NULL,
 severity VARCHAR(20) NOT NULL
);
CREATE TABLE audit_log (
 id UUID PRIMARY KEY, actor_id UUID, action VARCHAR(80) NOT NULL, entity_type VARCHAR(80), entity_id UUID,
 correlation_id VARCHAR(80), result VARCHAR(30) NOT NULL, metadata TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
