CREATE TABLE household (
 id UUID PRIMARY KEY, name VARCHAR(160) NOT NULL, owner_id UUID NOT NULL REFERENCES app_user(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE household_member (
 household_id UUID NOT NULL REFERENCES household(id) ON DELETE CASCADE, user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 role VARCHAR(20) NOT NULL, joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(household_id,user_id), CONSTRAINT ck_household_role CHECK(role IN ('OWNER','MEMBER'))
);
CREATE TABLE household_invitation (
 id UUID PRIMARY KEY, household_id UUID NOT NULL REFERENCES household(id) ON DELETE CASCADE, email VARCHAR(320) NOT NULL,
 token_hash VARCHAR(128) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 invited_by UUID NOT NULL REFERENCES app_user(id), accepted_by UUID REFERENCES app_user(id), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_invitation_status CHECK(status IN ('PENDING','ACCEPTED','EXPIRED','REVOKED'))
);
CREATE TABLE household_permission (household_id UUID NOT NULL REFERENCES household(id) ON DELETE CASCADE,user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,permission VARCHAR(60) NOT NULL,PRIMARY KEY(household_id,user_id,permission));

CREATE TABLE ingredient (
 id UUID PRIMARY KEY, household_id UUID REFERENCES household(id), owner_id UUID REFERENCES app_user(id), code VARCHAR(100) NOT NULL,
 name VARCHAR(180) NOT NULL, category VARCHAR(50) NOT NULL DEFAULT 'OTHER', base_unit VARCHAR(20) NOT NULL,
 calories_100 NUMERIC(10,2), protein_100 NUMERIC(10,2), carbohydrates_100 NUMERIC(10,2), fat_100 NUMERIC(10,2), fiber_100 NUMERIC(10,2),
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_ingredient_scope UNIQUE(household_id,owner_id,code)
);
CREATE TABLE recipe (id UUID PRIMARY KEY,household_id UUID REFERENCES household(id),owner_id UUID REFERENCES app_user(id),code VARCHAR(100) NOT NULL,name VARCHAR(180) NOT NULL,servings NUMERIC(8,2) NOT NULL DEFAULT 1,instructions TEXT,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE recipe_ingredient (id UUID PRIMARY KEY,recipe_id UUID NOT NULL REFERENCES recipe(id) ON DELETE CASCADE,ingredient_id UUID NOT NULL REFERENCES ingredient(id),quantity NUMERIC(12,3) NOT NULL,unit VARCHAR(20) NOT NULL,ingredient_order INTEGER NOT NULL);

CREATE TABLE nutrition_plan (id UUID PRIMARY KEY,owner_id UUID REFERENCES app_user(id),household_id UUID REFERENCES household(id),external_id VARCHAR(120) NOT NULL,name VARCHAR(180) NOT NULL,version INTEGER NOT NULL,status VARCHAR(30) NOT NULL,valid_from DATE,valid_until DATE,activated_at TIMESTAMPTZ,superseded_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,CONSTRAINT ck_nutrition_owner CHECK((owner_id IS NULL)<>(household_id IS NULL)),CONSTRAINT ck_nutrition_status CHECK(status IN ('DRAFT','ACTIVE','SUPERSEDED','ARCHIVED','CANCELLED')));
CREATE UNIQUE INDEX uk_nutrition_user_version ON nutrition_plan(owner_id,external_id,version) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX uk_nutrition_household_version ON nutrition_plan(household_id,external_id,version) WHERE household_id IS NOT NULL;
CREATE TABLE nutrition_plan_day (id UUID PRIMARY KEY,nutrition_plan_id UUID NOT NULL REFERENCES nutrition_plan(id) ON DELETE CASCADE,week_number INTEGER NOT NULL,day_number INTEGER NOT NULL,day_name VARCHAR(80),day_order INTEGER NOT NULL,UNIQUE(nutrition_plan_id,week_number,day_number));
CREATE TABLE planned_meal (id UUID PRIMARY KEY,nutrition_plan_day_id UUID NOT NULL REFERENCES nutrition_plan_day(id) ON DELETE CASCADE,recipe_id UUID NOT NULL REFERENCES recipe(id),meal_type VARCHAR(40) NOT NULL,meal_name VARCHAR(160) NOT NULL,meal_order INTEGER NOT NULL,UNIQUE(nutrition_plan_day_id,meal_order));
CREATE TABLE user_meal_portion (id UUID PRIMARY KEY,planned_meal_id UUID NOT NULL REFERENCES planned_meal(id) ON DELETE CASCADE,user_id UUID NOT NULL REFERENCES app_user(id),portion_multiplier NUMERIC(8,3) NOT NULL DEFAULT 1,quantity NUMERIC(12,3),calories NUMERIC(10,2),protein NUMERIC(10,2),carbohydrates NUMERIC(10,2),fat NUMERIC(10,2),UNIQUE(planned_meal_id,user_id));
CREATE TABLE meal_substitution (id UUID PRIMARY KEY,user_meal_portion_id UUID NOT NULL REFERENCES user_meal_portion(id) ON DELETE CASCADE,original_recipe_id UUID REFERENCES recipe(id),substitute_recipe_id UUID REFERENCES recipe(id),reason VARCHAR(255),created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);

CREATE TABLE shopping_list (id UUID PRIMARY KEY,household_id UUID NOT NULL REFERENCES household(id),nutrition_plan_id UUID REFERENCES nutrition_plan(id),week_number INTEGER NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'OPEN',manually_modified BOOLEAN NOT NULL DEFAULT FALSE,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE shopping_list_item (id UUID PRIMARY KEY,shopping_list_id UUID NOT NULL REFERENCES shopping_list(id) ON DELETE CASCADE,ingredient_id UUID REFERENCES ingredient(id),name VARCHAR(180) NOT NULL,category VARCHAR(50) NOT NULL,quantity NUMERIC(12,3),unit VARCHAR(20),purchased BOOLEAN NOT NULL DEFAULT FALSE,manual BOOLEAN NOT NULL DEFAULT FALSE,item_order INTEGER NOT NULL);
CREATE INDEX idx_household_member_user ON household_member(user_id);
CREATE INDEX idx_nutrition_plan_household ON nutrition_plan(household_id,status);
CREATE INDEX idx_shopping_list_household ON shopping_list(household_id,week_number);
