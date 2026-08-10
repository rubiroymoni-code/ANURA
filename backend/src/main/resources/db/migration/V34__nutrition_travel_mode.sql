CREATE TABLE nutrition_travel_mode (
 id UUID PRIMARY KEY,
 household_id UUID NOT NULL REFERENCES household(id) ON DELETE CASCADE,
 title VARCHAR(160) NOT NULL DEFAULT 'Modo viaje',
 start_date DATE NOT NULL,
 end_date DATE NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 general_guidance TEXT,
 exclude_from_adherence BOOLEAN NOT NULL DEFAULT TRUE,
 exclude_from_shopping BOOLEAN NOT NULL DEFAULT TRUE,
 created_by UUID NOT NULL REFERENCES app_user(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_travel_dates CHECK(end_date>=start_date),
 CONSTRAINT ck_travel_status CHECK(status IN ('DRAFT','ACTIVE','COMPLETED','CANCELLED'))
);
CREATE TABLE nutrition_travel_day (
 id UUID PRIMARY KEY,
 travel_mode_id UUID NOT NULL REFERENCES nutrition_travel_mode(id) ON DELETE CASCADE,
 travel_date DATE NOT NULL,
 plan_label VARCHAR(120) NOT NULL DEFAULT 'Comer fuera',
 guidance TEXT NOT NULL,
 UNIQUE(travel_mode_id,travel_date)
);
CREATE INDEX idx_nutrition_travel_dates ON nutrition_travel_mode(household_id,start_date,end_date,status);
