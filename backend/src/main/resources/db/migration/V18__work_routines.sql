CREATE TABLE user_work_profile (
 user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
 occupation VARCHAR(160),work_activity VARCHAR(30),rotating_shifts BOOLEAN NOT NULL DEFAULT FALSE,
 fridge_available BOOLEAN NOT NULL DEFAULT FALSE,microwave_available BOOLEAN NOT NULL DEFAULT FALSE,
 meal_break_minutes INTEGER,work_notes TEXT,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE daily_routine_template (
 id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,name VARCHAR(100) NOT NULL,
 work_start TIME,work_end TIME,training_moment VARCHAR(40),fasted_training BOOLEAN NOT NULL DEFAULT FALSE,
 breakfast_location VARCHAR(30),lunch_location VARCHAR(30),snack_location VARCHAR(30),dinner_location VARCHAR(30),
 portable_meals VARCHAR(200),days_of_week VARCHAR(40),notes TEXT,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE routine_calendar_assignment (
 user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,assignment_date DATE NOT NULL,
 template_id UUID NOT NULL REFERENCES daily_routine_template(id) ON DELETE CASCADE,notes TEXT,
 PRIMARY KEY(user_id,assignment_date)
);
CREATE INDEX idx_routine_assignment_date ON routine_calendar_assignment(user_id,assignment_date);
