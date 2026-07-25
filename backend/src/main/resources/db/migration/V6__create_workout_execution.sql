CREATE TABLE workout_session (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), workout_plan_id UUID REFERENCES workout_plan(id),
 workout_plan_version INTEGER, workout_plan_day_id UUID REFERENCES workout_plan_day(id), session_name VARCHAR(180) NOT NULL,
 planned_date DATE NOT NULL, started_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ, last_activity_at TIMESTAMPTZ NOT NULL,
 status VARCHAR(24) NOT NULL, duration_seconds INTEGER, global_rpe NUMERIC(4,1), energy_level INTEGER, pump_level INTEGER,
 pain_level INTEGER, difficulty_level INTEGER, notes TEXT, client_external_id UUID NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted_at TIMESTAMPTZ,
 CONSTRAINT ck_workout_session_status CHECK(status IN ('PLANNED','IN_PROGRESS','PAUSED','COMPLETED','ABANDONED','CANCELLED')),
 CONSTRAINT ck_workout_session_levels CHECK((global_rpe IS NULL OR global_rpe BETWEEN 1 AND 10) AND (energy_level IS NULL OR energy_level BETWEEN 0 AND 10) AND (pump_level IS NULL OR pump_level BETWEEN 0 AND 10) AND (pain_level IS NULL OR pain_level BETWEEN 0 AND 10) AND (difficulty_level IS NULL OR difficulty_level BETWEEN 0 AND 10)),
 CONSTRAINT uk_workout_session_client UNIQUE(user_id,client_external_id)
);
CREATE UNIQUE INDEX uk_workout_session_active ON workout_session(user_id) WHERE status IN ('IN_PROGRESS','PAUSED') AND deleted_at IS NULL;
CREATE INDEX idx_workout_session_user_date ON workout_session(user_id,planned_date DESC);
CREATE INDEX idx_workout_session_user_status ON workout_session(user_id,status);

CREATE TABLE exercise_performance (
 id UUID PRIMARY KEY, workout_session_id UUID NOT NULL REFERENCES workout_session(id), planned_exercise_id UUID REFERENCES planned_exercise(id),
 exercise_id UUID NOT NULL REFERENCES exercise(id), exercise_order INTEGER NOT NULL, original_exercise_id UUID REFERENCES exercise(id),
 substitution_reason VARCHAR(60), substitution_notes TEXT, pain_reported BOOLEAN NOT NULL DEFAULT FALSE, pain_area VARCHAR(100),
 pain_intensity INTEGER, notes TEXT, target_sets INTEGER, target_reps_min INTEGER, target_reps_max INTEGER,
 target_rir NUMERIC(4,1), target_rpe NUMERIC(4,1), target_rest_seconds INTEGER, target_instructions TEXT,
 started_at TIMESTAMPTZ, completed_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_exercise_pain CHECK(pain_intensity IS NULL OR pain_intensity BETWEEN 0 AND 10),
 CONSTRAINT uk_exercise_performance_order UNIQUE(workout_session_id,exercise_order)
);
CREATE INDEX idx_exercise_performance_session_order ON exercise_performance(workout_session_id,exercise_order);
CREATE INDEX idx_exercise_performance_exercise ON exercise_performance(exercise_id,created_at DESC);

CREATE TABLE set_performance (
 id UUID PRIMARY KEY, exercise_performance_id UUID NOT NULL REFERENCES exercise_performance(id), set_number INTEGER NOT NULL,
 set_type VARCHAR(24) NOT NULL, weight NUMERIC(10,2), repetitions INTEGER, rir NUMERIC(4,1), rpe NUMERIC(4,1),
 duration_seconds INTEGER, distance_meters NUMERIC(12,2), rest_seconds INTEGER, tempo VARCHAR(30), pain_level INTEGER,
 completed BOOLEAN NOT NULL DEFAULT FALSE, performed_at TIMESTAMPTZ, client_external_id UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted_at TIMESTAMPTZ,
 CONSTRAINT ck_set_type CHECK(set_type IN ('WARMUP','APPROACH','WORKING','FAILURE','TECHNIQUE','DROP_SET','BACK_OFF','OTHER')),
 CONSTRAINT ck_set_values CHECK((weight IS NULL OR weight >= 0) AND (repetitions IS NULL OR repetitions >= 0) AND (rir IS NULL OR rir BETWEEN 0 AND 10) AND (rpe IS NULL OR rpe BETWEEN 1 AND 10) AND (pain_level IS NULL OR pain_level BETWEEN 0 AND 10) AND (duration_seconds IS NULL OR duration_seconds >= 0) AND (distance_meters IS NULL OR distance_meters >= 0)),
 CONSTRAINT uk_set_performance_number UNIQUE(exercise_performance_id,set_number),
 CONSTRAINT uk_set_performance_client UNIQUE(exercise_performance_id,client_external_id)
);
CREATE INDEX idx_set_performance_exercise ON set_performance(exercise_performance_id,set_number) WHERE deleted_at IS NULL;

CREATE TABLE workout_personal_record (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), exercise_id UUID NOT NULL REFERENCES exercise(id),
 record_type VARCHAR(30) NOT NULL, value NUMERIC(14,3) NOT NULL, source_set_performance_id UUID NOT NULL REFERENCES set_performance(id),
 achieved_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_record_type CHECK(record_type IN ('MAX_WEIGHT','MAX_REPETITIONS','MAX_VOLUME','ESTIMATED_1RM','LONGEST_DISTANCE','BEST_TIME')),
 CONSTRAINT uk_personal_record_source UNIQUE(user_id,record_type,source_set_performance_id)
);
CREATE INDEX idx_personal_record_user_exercise ON workout_personal_record(user_id,exercise_id,achieved_at DESC);

CREATE TABLE workout_sync_operation (
 operation_id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), workout_session_id UUID REFERENCES workout_session(id),
 operation_type VARCHAR(40) NOT NULL, entity_type VARCHAR(40) NOT NULL, client_entity_id UUID, occurred_at TIMESTAMPTZ NOT NULL,
 result VARCHAR(30) NOT NULL, result_entity_id UUID, error_code VARCHAR(80), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_workout_sync_user_operation UNIQUE(user_id,operation_id)
);
CREATE INDEX idx_workout_sync_session ON workout_sync_operation(user_id,workout_session_id,created_at);
