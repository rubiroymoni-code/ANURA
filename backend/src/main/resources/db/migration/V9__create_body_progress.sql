CREATE TABLE body_checkin (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  checkin_date DATE NOT NULL,
  weight NUMERIC(7,2) NOT NULL,
  body_fat_percentage NUMERIC(5,2),
  waist_cm NUMERIC(6,2),
  chest_cm NUMERIC(6,2),
  hip_cm NUMERIC(6,2),
  left_arm_cm NUMERIC(6,2),
  right_arm_cm NUMERIC(6,2),
  left_thigh_cm NUMERIC(6,2),
  right_thigh_cm NUMERIC(6,2),
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_body_checkin_user_date UNIQUE(user_id,checkin_date),
  CONSTRAINT ck_body_weight CHECK(weight BETWEEN 20 AND 500),
  CONSTRAINT ck_body_fat CHECK(body_fat_percentage IS NULL OR body_fat_percentage BETWEEN 1 AND 80),
  CONSTRAINT ck_body_measurements CHECK(
    (waist_cm IS NULL OR waist_cm BETWEEN 10 AND 300) AND
    (chest_cm IS NULL OR chest_cm BETWEEN 10 AND 300) AND
    (hip_cm IS NULL OR hip_cm BETWEEN 10 AND 300) AND
    (left_arm_cm IS NULL OR left_arm_cm BETWEEN 10 AND 150) AND
    (right_arm_cm IS NULL OR right_arm_cm BETWEEN 10 AND 150) AND
    (left_thigh_cm IS NULL OR left_thigh_cm BETWEEN 10 AND 200) AND
    (right_thigh_cm IS NULL OR right_thigh_cm BETWEEN 10 AND 200))
);
CREATE INDEX idx_body_checkin_user_date ON body_checkin(user_id,checkin_date DESC);

CREATE TABLE progress_photo (
  id UUID PRIMARY KEY,
  body_checkin_id UUID NOT NULL REFERENCES body_checkin(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  photo_type VARCHAR(16) NOT NULL,
  storage_url TEXT NOT NULL,
  thumbnail_url TEXT,
  taken_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_progress_photo_type CHECK(photo_type IN ('FRONT','SIDE','BACK','OTHER')),
  CONSTRAINT uk_progress_photo_type UNIQUE(body_checkin_id,photo_type)
);
CREATE INDEX idx_progress_photo_user_checkin ON progress_photo(user_id,body_checkin_id);
