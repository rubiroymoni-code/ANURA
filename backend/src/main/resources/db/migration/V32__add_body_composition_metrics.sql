ALTER TABLE body_checkin
  ADD COLUMN muscle_mass_kg NUMERIC(6,2),
  ADD COLUMN visceral_fat_percentage NUMERIC(5,2),
  ADD COLUMN subcutaneous_fat_percentage NUMERIC(5,2);
ALTER TABLE body_checkin ADD CONSTRAINT ck_muscle_mass CHECK (muscle_mass_kg IS NULL OR muscle_mass_kg BETWEEN 1 AND 300);
ALTER TABLE body_checkin ADD CONSTRAINT ck_visceral_fat CHECK (visceral_fat_percentage IS NULL OR visceral_fat_percentage BETWEEN 0 AND 100);
ALTER TABLE body_checkin ADD CONSTRAINT ck_subcutaneous_fat CHECK (subcutaneous_fat_percentage IS NULL OR subcutaneous_fat_percentage BETWEEN 0 AND 100);
