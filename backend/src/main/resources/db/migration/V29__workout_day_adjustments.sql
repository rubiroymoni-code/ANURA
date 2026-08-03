CREATE TABLE workout_day_adjustment (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id),
 workout_plan_id UUID NOT NULL REFERENCES workout_plan(id) ON DELETE CASCADE,
 workout_plan_day_id UUID NOT NULL REFERENCES workout_plan_day(id) ON DELETE CASCADE,
 original_date DATE NOT NULL,
 scheduled_date DATE,
 status VARCHAR(20) NOT NULL,
 reason VARCHAR(240),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_workout_day_adjustment_status CHECK(status IN ('MOVED','SKIPPED')),
 CONSTRAINT uk_workout_day_adjustment UNIQUE(user_id,workout_plan_day_id,original_date)
);
CREATE INDEX idx_workout_day_adjustment_schedule ON workout_day_adjustment(user_id,scheduled_date,status);
