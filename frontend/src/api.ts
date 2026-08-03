export const API_BASE = import.meta.env.VITE_API_URL || "/api/v1";
export type EntryType = "WORKOUT" | "MEAL" | "WEIGHT" | "MEASUREMENT" | "GOAL";
export type Entry = {
  id: string;
  type: EntryType;
  title: string;
  entryDate: string;
  value?: number;
  unit?: string;
  details?: string;
  notes?: string;
  completed: boolean;
};
export type User = { id: string; email: string; displayName: string };
export type ProgressPhoto={id:string;photoType:"FRONT"|"SIDE"|"BACK"|"OTHER";storageUrl:string;thumbnailUrl?:string;takenAt:string};
export type BodyCheckin={id:string;checkinDate:string;weight:number;bodyFatPercentage?:number;waistCm?:number;chestCm?:number;hipCm?:number;leftArmCm?:number;rightArmCm?:number;leftThighCm?:number;rightThighCm?:number;notes?:string;createdAt:string;updatedAt:string;photos:ProgressPhoto[]};
export type BodyCheckinInput=Omit<BodyCheckin,"id"|"createdAt"|"updatedAt"|"photos">;
export type EvolutionPoint={date:string;weight:number;movingAverage7d?:number;waistCm?:number;chestCm?:number;hipCm?:number;leftArmCm?:number;rightArmCm?:number;leftThighCm?:number;rightThighCm?:number};
export type BodyEvolution={from:string;to:string;points:EvolutionPoint[];totalWeightChange?:number;previousWeightChange?:number;minimumWeight?:number;maximumWeight?:number;checkinCount:number;trend:"UP"|"DOWN"|"STABLE";weeklyStreak:number};
export type Supplement={id:string;name:string;dose?:string;schedule?:string;purpose?:string;notes?:string;active:boolean};
export type SupplementInput=Omit<Supplement,"id">;
export type NutritionPreferences={liked_foods?:string;disliked_foods?:string;exclusions?:string;usual_drinks?:string;pantry_staples?:string;cooking_notes?:string;planning_notes?:string;minimize_waste:boolean;practical_portions:boolean};

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem("anura-token");
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      ...(init.body instanceof FormData
        ? {}
        : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    const text = await response.text();
    try {
      const problem=JSON.parse(text) as {message?:string;correlationId?:string};
      throw new Error(`${problem.message || "No se pudo completar la operación"}${problem.correlationId ? ` · Ref. ${problem.correlationId}` : ""}`);
    } catch (error) {
      if (error instanceof SyntaxError) throw new Error(text || "No se pudo completar la operación");
      throw error;
    }
  }
  if (response.status === 204) return undefined as T;
  const responseBody = await response.text();
  return responseBody ? JSON.parse(responseBody) as T : undefined as T;
}
export const api = {
  auth: (mode: "login" | "register", data: object) =>
    request<{ token: string; user: User }>(`/auth/${mode}`, {
      method: "POST",
      body: JSON.stringify(data),
    }),
  createRecoveryCode: () =>
    request<{ code: string; expiresAt: string }>("/auth/recovery-code", {
      method: "POST",
    }),
  resetPassword: (data: { email: string; code: string; newPassword: string }) =>
    request<void>("/auth/password-reset", {
      method: "POST",
      body: JSON.stringify(data),
    }),
  requestPasswordRecovery: (email:string) => request<void>("/auth/password-recovery/request",{method:"POST",body:JSON.stringify({email})}),
  entries: (type?: EntryType) =>
    request<Entry[]>(`/entries${type ? `?type=${type}` : ""}`),
  create: (data: Omit<Entry, "id">) =>
    request<Entry>("/entries", { method: "POST", body: JSON.stringify(data) }),
  update: (id:string,data:Omit<Entry,"id">)=>request<Entry>(`/entries/${id}`,{method:"PUT",body:JSON.stringify(data)}),
  remove: (id: string) => request<void>(`/entries/${id}`, { method: "DELETE" }),
  profilePreferences:()=>request<{primary_goal?:string;experience_level?:string;activity_level?:string;height_cm?:number;training_days?:number;limitations?:string;biological_sex?:string;avatar_url?:string;reminder_email_enabled?:boolean;reminder_frequency?:string;last_summary_sent_at?:string}>("/profile/preferences"),
  saveProfilePreferences:(data:{primaryGoal:string;experienceLevel:string;activityLevel:string;heightCm?:number;trainingDays?:number;limitations?:string;biologicalSex?:string;reminderEmailEnabled:boolean})=>request<void>("/profile/preferences",{method:"PATCH",body:JSON.stringify(data)}),
  cycles:()=>request<Array<{id:string;start_date:string;end_date?:string;flow_level?:string;symptoms?:string;notes?:string}>>("/profile/cycles"),
  saveCycle:(data:{startDate:string;endDate?:string;flowLevel?:string;symptoms?:string;notes?:string})=>request<Record<string,unknown>>("/profile/cycles",{method:"POST",body:JSON.stringify(data)}),
  deleteCycle:(id:string)=>request<void>(`/profile/cycles/${id}`,{method:"DELETE"}),
  supplements:()=>request<Supplement[]>("/profile/supplements"),
  saveSupplement:(data:SupplementInput)=>request<Supplement>("/profile/supplements",{method:"POST",body:JSON.stringify(data)}),
  updateSupplement:(id:string,data:SupplementInput)=>request<Supplement>(`/profile/supplements/${id}`,{method:"PATCH",body:JSON.stringify(data)}),
  deleteSupplement:(id:string)=>request<void>(`/profile/supplements/${id}`,{method:"DELETE"}),
  changePassword:(data:{currentPassword:string;newPassword:string})=>request<void>("/profile/password",{method:"PATCH",body:JSON.stringify(data)}),
  testReminder:()=>request<void>("/profile/reminders/test",{method:"POST"}),
  saveAvatar:(avatarUrl:string)=>request<void>("/profile/avatar",{method:"PATCH",body:JSON.stringify({avatarUrl})}),
};
export const bodyProgressApi={
  list:()=>request<BodyCheckin[]>("/body-checkins"),
  latest:()=>request<BodyCheckin|null>("/body-checkins/latest"),
  evolution:(from?:string,to?:string)=>request<BodyEvolution>(`/body-checkins/evolution${from&&to?`?from=${from}&to=${to}`:""}`),
  create:(body:BodyCheckinInput)=>request<BodyCheckin>("/body-checkins",{method:"POST",body:JSON.stringify(body)}),
  update:(id:string,body:BodyCheckinInput)=>request<BodyCheckin>(`/body-checkins/${id}`,{method:"PUT",body:JSON.stringify(body)}),
  remove:(id:string)=>request<void>(`/body-checkins/${id}`,{method:"DELETE"}),
  photoStorage:()=>request<{enabled:boolean}>("/body-checkins/photo-storage"),
  addPhoto:(id:string,body:{photoType:string;storageUrl:string;thumbnailUrl?:string;takenAt:string})=>request<ProgressPhoto>(`/body-checkins/${id}/photos`,{method:"POST",body:JSON.stringify(body)}),
  removePhoto:(id:string,photoId:string)=>request<void>(`/body-checkins/${id}/photos/${photoId}`,{method:"DELETE"}),
};
export type ImportIssue = {
  row?: number;
  column?: string;
  code: string;
  message: string;
  severity: string;
};
export type ImportPreview = {
  importJobId: string;
  status: string;
  confirmable: boolean;
  planExternalId: string;
  planName: string;
  version: number;
  weeks: number;
  days: number;
  exercises: number;
  validFrom?: string;
  validUntil?: string;
  issues: ImportIssue[];
};
export const trainingApi = {
  preview: (file: File) => {
    const body = new FormData();
    body.append("file", file);
    return request<ImportPreview>("/imports/training-plans/preview", {
      method: "POST",
      body,
    });
  },
  confirm: (id: string) =>
    request<{ planId: string; status: string }>(`/imports/${id}/confirm`, {
      method: "POST",
    }),
  activate: (id: string) =>
    request<{ id: string; name: string; version: number; status: string }>(
      `/workout-plans/${id}/activate`,
      { method: "POST" },
    ),
  plans: () =>
    request<
      Array<{ id: string; name: string; version: number; status: string }>
    >("/workout-plans"),
};

export type WorkoutSet = { id:string; setNumber:number; setType:string; weight?:number; repetitions?:number; rir?:number; rpe?:number; durationSeconds?:number; distanceMeters?:number; restSeconds?:number; tempo?:string; painLevel?:number; completed:boolean; clientExternalId:string };
export type WorkoutExercise = { id:string; exerciseId:string; name:string; muscleGroup?:string; equipment?:string; order:number; targetSets?:number; targetRepsMin?:number; targetRepsMax?:number; targetRir?:number; targetRpe?:number; targetRestSeconds?:number; instructions?:string; completed:boolean; painReported:boolean; painArea?:string;painIntensity?:number;notes?:string; sets:WorkoutSet[] };
export type WorkoutSession = { header:{id:string;name:string;plannedDate:string;status:string;startedAt:string;completedAt?:string;durationSeconds?:number;globalRpe?:number;energyLevel?:number;pumpLevel?:number;painLevel?:number;difficultyLevel?:number;notes?:string;clientExternalId:string;workoutPlanVersion?:number;pausedAt?:string;pausedSeconds:number}; exercises:WorkoutExercise[]; metrics:{exercises:number;sets:number;repetitions:number;volume:number;maxPain:number;personalRecords:number} };
export type TodayWorkout = { planId:string;planName:string;planVersion:number;dayId:string;sessionName:string;dayName:string;weekNumber:number;dayNumber:number;estimatedMinutes:number;exerciseCount:number;exercises:Array<{exerciseId:string;name:string;muscleGroup?:string;sets:number;repsMin:number;repsMax:number;targetRir?:number;restSeconds?:number}> };
export type WorkoutSummary = {id:string;name:string;date:string;status:string;durationSeconds?:number;globalRpe?:number;exercises:number;sets:number;volume:number};
export type WorkoutPlan = {id:string;name:string;version:number;status:string;validFrom?:string;validUntil?:string};
export type PlannedWorkoutExercise = {weekNumber:number;dayNumber:number;dayName?:string;sessionName:string;order:number;exercise:string;muscleGroup?:string;equipment?:string;sets:number;repsMin:number;repsMax:number;targetRir?:number;targetRpe?:number;restSeconds?:number;tempo?:string;warmupRequired:boolean;supersetGroup?:string;alternativeExerciseCode?:string;instructions?:string;notes?:string};
export const workoutApi = {
  today:()=>request<TodayWorkout|null>("/workouts/today"),
  active:()=>request<WorkoutSession|null>("/workout-sessions/active"),
  history:(page=0,size=30)=>request<WorkoutSummary[]>(`/workout-sessions?page=${page}&size=${size}`),
  plans:()=>request<WorkoutPlan[]>("/workout-plans"),
  planDetails:(id:string)=>request<PlannedWorkoutExercise[]>(`/workout-plans/${id}/details`),
  deletePlan:(id:string)=>request<void>(`/workout-plans/${id}`,{method:"DELETE"}),
  one:(id:string)=>request<WorkoutSession>(`/workout-sessions/${id}`),
  deleteSession:(id:string)=>request<void>(`/workout-sessions/${id}`,{method:"DELETE"}),
  start:(body:{workoutPlanDayId?:string;name?:string;clientExternalId:string})=>request<WorkoutSession>("/workout-sessions",{method:"POST",body:JSON.stringify(body)}),
  pause:(id:string)=>request<WorkoutSession>(`/workout-sessions/${id}/pause`,{method:"POST"}),
  resume:(id:string)=>request<WorkoutSession>(`/workout-sessions/${id}/resume`,{method:"POST"}),
  complete:(id:string,body:object)=>request<WorkoutSession>(`/workout-sessions/${id}/complete`,{method:"POST",body:JSON.stringify(body)}),
  abandon:(id:string,reason?:string)=>request<WorkoutSession>(`/workout-sessions/${id}/abandon`,{method:"POST",body:JSON.stringify({reason})}),
  addExercise:(session:string,body:object)=>request<WorkoutExercise>(`/workout-sessions/${session}/exercises`,{method:"POST",body:JSON.stringify(body)}),
  addSet:(session:string,exercise:string,body:object)=>request<WorkoutSet>(`/workout-sessions/${session}/exercises/${exercise}/sets`,{method:"POST",body:JSON.stringify(body)}),
  updateSet:(session:string,exercise:string,set:string,body:object)=>request<WorkoutSet>(`/workout-sessions/${session}/exercises/${exercise}/sets/${set}`,{method:"PATCH",body:JSON.stringify(body)}),
  deleteSet:(session:string,exercise:string,set:string)=>request<void>(`/workout-sessions/${session}/exercises/${exercise}/sets/${set}`,{method:"DELETE"}),
  finishExercise:(session:string,exercise:string)=>request<WorkoutExercise>(`/workout-sessions/${session}/exercises/${exercise}/complete`,{method:"POST"}),
  pain:(session:string,exercise:string,body:object)=>request<WorkoutExercise>(`/workout-sessions/${session}/exercises/${exercise}/pain`,{method:"PATCH",body:JSON.stringify(body)}),
  exercises:()=>request<Array<{id:string;name:string;muscleGroup?:string;equipment?:string}>>("/exercises"),
  substitute:(session:string,exercise:string,body:object)=>request<WorkoutExercise>(`/workout-sessions/${session}/exercises/${exercise}/substitute`,{method:"POST",body:JSON.stringify(body)}),
  lastPerformance:(exercise:string)=>request<{weight?:number;repetitions?:number;rir?:number;rpe?:number}|null>(`/exercises/${exercise}/last-performance`),
  sync:(session:string,body:object[])=>request<Array<{operationId:string;result:string;entityId?:string;errorCode?:string}>>(`/workout-sessions/${session}/sync`,{method:"POST",body:JSON.stringify(body)}),
};
export type Household = { id: string; name: string; role: string };
export type NutritionImportPreview = {
  importJobId: string;
  status: string;
  confirmable: boolean;
  planName?: string;
  version?: number;
  rows: number;
  recipes: number;
  ingredients: number;
  users: string[];
  issues: Array<{ row?: number; column?: string; message: string }>;
};
export type MealStatus = "PENDING"|"COMPLETED"|"PARTIAL"|"SKIPPED"|"SUBSTITUTED";
export type MealType = "BREAKFAST"|"MID_MORNING"|"LUNCH"|"SNACK"|"DINNER"|"OTHER";
export type MealInput = {mealType:MealType;name:string;date?:string;portion?:string;calories?:number;protein?:number;carbohydrates?:number;fat?:number;notes?:string};
export type TodayMeal = { planned_meal_id:string;plan_id:string;plan_name:string;version:number;day_name:string;meal_type:string;meal_name:string;recipe:string;calories:number;protein:number;carbohydrates:number;fat:number;portion_multiplier:number;status:MealStatus;consumed_meal_id?:string;custom_name?:string;notes?:string;completed_at?:string };
export type NutritionDashboard={target:Partial<Record<"calories"|"protein"|"carbohydrates"|"fat"|"fiber",number>>;planned:Record<"calories"|"protein"|"carbohydrates"|"fat",number>;consumed:Record<"calories"|"protein"|"carbohydrates"|"fat",number>;week:Array<{date:string;calories:number}>};
export type AdherenceDashboard={days:number;meals:{completed:number;substituted:number;partial:number;skipped:number;missing:number;expected:number;score:number};workouts:{completed:number;partial:number;abandoned:number;missing:number;expected:number;score:number};patterns:Array<{day_number:number;incidents:number}>;weekly:Array<{week:string;meal_score?:number;workout_score?:number}>;currentWeek?:{expected:number;completed:number;score:number;complete:boolean};workoutReasons:Array<{reason:string;incidents:number}>};
export type ConsumedMeal={id:string;meal_date:string;meal_type:string;status:MealStatus;name?:string;portion?:string;calories?:number;protein?:number;carbohydrates?:number;fat?:number;notes?:string;adherence_percent?:number;deviation_reason?:string;completed_at?:string;planned_meal?:string;planned_recipe?:string};
export const householdApi = {
  list: () => request<Household[]>("/households"),
  create: (name: string) =>
    request<{household:Household;invitation:{code:string;expiresAt:string}}>("/households", {
      method: "POST",
      body: JSON.stringify({ name }),
    }),
  members: (id: string) =>
    request<
      Array<{ id: string; email: string; display_name: string; role: string }>
    >(`/households/${id}/members`),
  promptContext:(id:string)=>request<Record<string,unknown>>(`/households/${id}/prompt-context`),
  rename: (id:string,name:string)=>request<Household>(`/households/${id}`,{method:"PATCH",body:JSON.stringify({name})}),
  leave: (id:string)=>request<void>(`/households/${id}/leave`,{method:"POST"}),
  remove: (id:string)=>request<void>(`/households/${id}`,{method:"DELETE"}),
  invite: (id: string, email?: string) =>
    request<{ code: string; expiresAt: string;recipientStatus:"REGISTERED_USER"|"NEW_USER"|"SHAREABLE_CODE";deliveryStatus:"SENT"|"FAILED"|"EMAIL_DISABLED"|"NOT_REQUESTED" }>(
      `/households/${id}/invitations`,
      { method: "POST", body: JSON.stringify({ email: email || null }) },
    ),
  accept: (code: string) =>
    request<void>("/households/invitations/accept", {
      method: "POST",
      body: JSON.stringify({ code }),
    }),
};
export type WorkRoutineData={profile:Record<string,unknown>;templates:Array<Record<string,unknown>&{id:string;name:string}>;calendar:Array<Record<string,unknown>&{assignment_date:string;template_id:string;name:string}>};
export const workRoutineApi={
  get:()=>request<WorkRoutineData>("/profile/work-routine"),
  saveProfile:(data:object)=>request<void>("/profile/work-routine/profile",{method:"PUT",body:JSON.stringify(data)}),
  addTemplate:(data:object)=>request<{id:string;name:string}>("/profile/work-routine/templates",{method:"POST",body:JSON.stringify(data)}),
  deleteTemplate:(id:string)=>request<void>(`/profile/work-routine/templates/${id}`,{method:"DELETE"}),
  assign:(date:string,templateId:string,notes?:string)=>request<void>(`/profile/work-routine/calendar/${date}`,{method:"PUT",body:JSON.stringify({templateId,notes})}),
  unassign:(date:string)=>request<void>(`/profile/work-routine/calendar/${date}`,{method:"DELETE"}),
};
export const nutritionApi = {
  preferences:()=>request<NutritionPreferences>("/nutrition/preferences"),
  savePreferences:(data:{likedFoods:string;dislikedFoods:string;exclusions:string;usualDrinks:string;pantryStaples:string;cookingNotes:string;planningNotes:string;minimizeWaste:boolean;practicalPortions:boolean})=>request<void>("/nutrition/preferences",{method:"PUT",body:JSON.stringify(data)}),
  today:()=>request<TodayMeal[]>("/nutrition/today"),
  dashboard:()=>request<NutritionDashboard>("/nutrition/dashboard"),
  adherence:(days=28)=>request<AdherenceDashboard>(`/nutrition/adherence?days=${days}`),
  consumedMeals:(from="2000-01-01")=>request<ConsumedMeal[]>(`/nutrition/consumed-meals?from=${from}`),
  targets:()=>request<Array<Record<string,number|string>>>("/nutrition/targets"),
  saveTarget:(data:{validFrom:string;calories:number;protein?:number;carbohydrates?:number;fat?:number;fiber?:number})=>request<void>("/nutrition/targets",{method:"PUT",body:JSON.stringify(data)}),
  completeToday:(id:string)=>request<Record<string,unknown>>(`/nutrition/today/${id}/complete`,{method:"POST"}),
  undoToday:(id:string)=>request<void>(`/nutrition/today/${id}/completion`,{method:"DELETE"}),
  skipToday:(id:string,notes?:string)=>request<Record<string,unknown>>(`/nutrition/today/${id}/skip`,{method:"POST",body:JSON.stringify({notes})}),
  substituteToday:(id:string,data:Omit<MealInput,"mealType"|"date">)=>request<Record<string,unknown>>(`/nutrition/today/${id}/substitute`,{method:"POST",body:JSON.stringify(data)}),
  additionalToday:(id:string,data:MealInput)=>request<Record<string,unknown>>(`/nutrition/today/${id}/additional`,{method:"POST",body:JSON.stringify(data)}),
  partialToday:(id:string,data:{percent:number;reason?:string;portion?:string;notes?:string})=>request<Record<string,unknown>>(`/nutrition/today/${id}/partial`,{method:"POST",body:JSON.stringify(data)}),
  customMeal:(data:MealInput)=>request<Record<string,unknown>>("/nutrition/meals/custom",{method:"POST",body:JSON.stringify(data)}),
  updateConsumed:(id:string,data:MealInput)=>request<Record<string,unknown>>(`/nutrition/consumed-meals/${id}`,{method:"PATCH",body:JSON.stringify(data)}),
  deleteConsumed:(id:string)=>request<void>(`/nutrition/consumed-meals/${id}`,{method:"DELETE"}),
  recipes: () =>
    request<
      Array<{ id: string; code: string; name: string; servings: number }>
    >("/nutrition/recipes"),
  plans: () =>
    request<
      Array<{ id: string; name: string; version: number; status: string }>
    >("/nutrition/plans"),
  week: (id: string) =>
    request<Array<Record<string, unknown>>>(`/nutrition/plans/${id}/week`),
  planDetails: (id:string) => request<Array<Record<string,unknown>>>(`/nutrition/plans/${id}/details`),
  summary: (id: string) =>
    request<Array<Record<string, unknown>>>(`/nutrition/plans/${id}/summary`),
  activate: (id: string) =>
    request<void>(`/nutrition/plans/${id}/activate`, { method: "POST" }),
  deletePlan:(id:string)=>request<void>(`/nutrition/plans/${id}`,{method:"DELETE"}),
  recipe: (id: string) =>
    request<Array<Record<string, unknown>>>(`/nutrition/recipes/${id}`),
  mealPortions:(id:string)=>request<Array<Record<string,unknown>>>(`/nutrition/meals/${id}/portions`),
  shopping: () =>
    request<
      Array<{
        id: string;
        nutrition_plan_id: string;
        week_number: number;
        status: string;
        manually_modified: boolean;
      }>
    >("/nutrition/shopping-lists"),
  generateShopping: (planId: string, week = 1, replaceModified = false) =>
    request<{ id: string; items: number }>(
      `/nutrition/plans/${planId}/shopping-list?week=${week}&replaceModified=${replaceModified}`,
      { method: "POST" },
    ),
  items: (id: string) =>
    request<
      Array<{
        id: string;
        name: string;
        category: string;
        quantity: number;
        required_quantity: number;
        pantry_used: number;
        unit: string;
        purchased: boolean;
        manual: boolean;
      }>
    >(`/nutrition/shopping-lists/${id}/items`),
  addShoppingItem: (id: string, body: {name:string;category:string;quantity:number;unit:string}) =>
    request<{id:string;name:string}>(`/nutrition/shopping-lists/${id}/items`, {method:"POST",body:JSON.stringify(body)}),
  shoppingQuantity: (id: string, quantity: number) =>
    request<void>(`/nutrition/shopping-items/${id}/quantity`, {method:"PATCH",body:JSON.stringify({quantity})}),
  resetShopping:(id:string)=>request<void>(`/nutrition/shopping-lists/${id}`,{method:"DELETE"}),
  pantry:()=>request<Array<{ingredient_id:string;name:string;category:string;quantity:number;unit:string}>>("/nutrition/pantry"),
  addPantry:(body:{name:string;category:string;quantity:number;unit:string})=>request<{ingredientId:string;name:string}>("/nutrition/pantry",{method:"POST",body:JSON.stringify(body)}),
  updatePantry:(ingredientId:string,quantity:number,unit:string)=>request<void>(`/nutrition/pantry/${ingredientId}`,{method:"PATCH",body:JSON.stringify({quantity,unit})}),
  deletePantry:(ingredientId:string,unit:string)=>request<void>(`/nutrition/pantry/${ingredientId}?unit=${encodeURIComponent(unit)}`,{method:"DELETE"}),
  clearPantry:()=>request<void>("/nutrition/pantry",{method:"DELETE"}),
  toggle: (id: string) =>
    request<void>(`/nutrition/shopping-items/${id}/toggle`, {
      method: "PATCH",
    }),
  preview: (type: "diet" | "shared-diet" | "recipes", file: File) => {
    const body = new FormData();
    body.append("file", file);
    return request<NutritionImportPreview>(
      `/imports/nutrition/${type}/preview`,
      { method: "POST", body },
    );
  },
  confirm: (id: string) =>
    request<{ status: string }>(`/imports/nutrition/${id}/confirm`, {
      method: "POST",
    }),
};
