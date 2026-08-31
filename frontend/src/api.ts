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
export const localDate = () => { const date = new Date(); return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,"0")}-${String(date.getDate()).padStart(2,"0")}`; };
export type SleepDay={sleep_date:string;total_sleep_minutes:number;quality_score?:number;morning_energy?:number};
export type SleepSession=SleepDay & {id:string;bed_time?:string;wake_time?:string;notes?:string};
export type SleepInput={sleepDate:string;totalSleepMinutes:number;qualityScore?:number;morningEnergy?:number;bedTime?:string;wakeTime?:string;notes?:string};
export type SleepSummary={days:number;records:number;averageSleepMinutes:number;averageQuality:number;averageEnergy:number;goalMinutes:number;goalCompletionPercentage:number;sleepDebtMinutes:number;currentStreak:number;series:SleepDay[]};
export const sleepApi={today:async(date=localDate())=>(await request<SleepSession|null|undefined>(`/sleep/today?date=${date}`)) ?? null,save:(body:SleepInput)=>request<SleepSession>("/sleep",{method:"POST",body:JSON.stringify(body)}),update:(id:string,body:SleepInput)=>request<SleepSession>(`/sleep/${id}`,{method:"PUT",body:JSON.stringify(body)}),remove:(id:string)=>request<void>(`/sleep/${id}`,{method:"DELETE"}),summary:(days:number,to=localDate())=>request<SleepSummary>(`/sleep/summary?days=${days}&to=${to}`)};
export type ProgressPhoto={id:string;photoType:"FRONT"|"SIDE"|"BACK"|"OTHER";storageUrl:string;thumbnailUrl?:string;takenAt:string};
export type BodyCheckin={id:string;checkinDate:string;weight:number;bodyFatPercentage?:number;muscleMassKg?:number;visceralFatPercentage?:number;subcutaneousFatPercentage?:number;waistCm?:number;chestCm?:number;hipCm?:number;leftArmCm?:number;rightArmCm?:number;leftThighCm?:number;rightThighCm?:number;notes?:string;createdAt:string;updatedAt:string;photos:ProgressPhoto[]};
export type BodyCheckinInput=Omit<BodyCheckin,"id"|"createdAt"|"updatedAt"|"photos">;
export type EvolutionPoint={date:string;weight:number;movingAverage7d?:number;waistCm?:number;chestCm?:number;hipCm?:number;leftArmCm?:number;rightArmCm?:number;leftThighCm?:number;rightThighCm?:number};
export type BodyEvolution={from:string;to:string;points:EvolutionPoint[];totalWeightChange?:number;previousWeightChange?:number;minimumWeight?:number;maximumWeight?:number;checkinCount:number;trend:"UP"|"DOWN"|"STABLE";weeklyStreak:number};
export type Supplement={id:string;name:string;dose?:string;schedule?:string;purpose?:string;notes?:string;active:boolean};
export type SupplementInput=Omit<Supplement,"id">;
export type NutritionPreferences={liked_foods?:string;disliked_foods?:string;exclusions?:string;usual_drinks?:string;pantry_staples?:string;cooking_notes?:string;planning_notes?:string;minimize_waste:boolean;practical_portions:boolean};
export type ReminderSettings={checkin_email:boolean;nutrition_plan_email:boolean;workout_plan_email:boolean;pantry_email:boolean;checkin_push:boolean;nutrition_plan_push:boolean;workout_plan_push:boolean;pantry_push:boolean;daily_tracking_push:boolean;daily_tracking_email:boolean;daily_tracking_time:string};
export type PendingReminder={type:string;title:string;message:string;action:string;priority:number;reference_id?:string};
export type CustomReminder={id:string;title:string;details?:string;frequency:"DAILY"|"WEEKLY";reminder_time:string;day_of_week?:number;email_enabled:boolean;in_app_enabled:boolean;enabled:boolean;next_due_at:string};
export type ReminderCenter={settings:ReminderSettings;pending:PendingReminder[];custom:CustomReminder[]};
export type PushConfig={enabled:boolean;publicKey:string;devices:number};

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
  updateCycle:(id:string,data:{startDate:string;endDate?:string;flowLevel?:string;symptoms?:string;notes?:string})=>request<Record<string,unknown>>(`/profile/cycles/${id}`,{method:"PUT",body:JSON.stringify(data)}),
  deleteCycle:(id:string)=>request<void>(`/profile/cycles/${id}`,{method:"DELETE"}),
  supplements:()=>request<Supplement[]>("/profile/supplements"),
  saveSupplement:(data:SupplementInput)=>request<Supplement>("/profile/supplements",{method:"POST",body:JSON.stringify(data)}),
  updateSupplement:(id:string,data:SupplementInput)=>request<Supplement>(`/profile/supplements/${id}`,{method:"PATCH",body:JSON.stringify(data)}),
  deleteSupplement:(id:string)=>request<void>(`/profile/supplements/${id}`,{method:"DELETE"}),
  changePassword:(data:{currentPassword:string;newPassword:string})=>request<void>("/profile/password",{method:"PATCH",body:JSON.stringify(data)}),
  testReminder:()=>request<void>("/profile/reminders/test",{method:"POST"}),
  reminders:()=>request<ReminderCenter>("/reminders"),
  saveReminderSettings:(data:{checkinEmail:boolean;nutritionPlanEmail:boolean;workoutPlanEmail:boolean;pantryEmail:boolean;checkinPush:boolean;nutritionPlanPush:boolean;workoutPlanPush:boolean;pantryPush:boolean;dailyTrackingPush:boolean;dailyTrackingEmail:boolean;dailyTrackingTime:string})=>request<void>("/reminders/settings",{method:"PUT",body:JSON.stringify(data)}),
  createReminder:(data:{title:string;details?:string;frequency:string;time:string;dayOfWeek?:number;emailEnabled:boolean;inAppEnabled:boolean})=>request<CustomReminder>("/reminders/custom",{method:"POST",body:JSON.stringify(data)}),
  updateReminder:(id:string,data:{title:string;details?:string;frequency:string;time:string;dayOfWeek?:number;emailEnabled:boolean;inAppEnabled:boolean})=>request<CustomReminder>(`/reminders/custom/${id}`,{method:"PUT",body:JSON.stringify(data)}),
  deleteReminder:(id:string)=>request<void>(`/reminders/custom/${id}`,{method:"DELETE"}),
  acknowledgeReminder:(id:string)=>request<void>(`/reminders/custom/${id}/ack`,{method:"POST"}),
  sendAnuraGuide:()=>request<void>("/reminders/guide",{method:"POST"}),
  pushConfig:()=>request<PushConfig>("/reminders/push/config"),
  savePushSubscription:(data:{endpoint:string;p256dh:string;auth:string;deviceName:string})=>request<{subscribed:boolean}>("/reminders/push/subscriptions",{method:"POST",body:JSON.stringify(data)}),
  deletePushSubscription:(endpoint:string)=>request<void>("/reminders/push/subscriptions",{method:"DELETE",body:JSON.stringify({endpoint})}),
  testPush:()=>request<void>("/reminders/push/test",{method:"POST"}),
  scheduleRestPush:(timerId:string,endAt:string,sessionId:string)=>request<void>("/reminders/push/rest-timers",{method:"POST",body:JSON.stringify({timerId,endAt,sessionId})}),
  cancelRestPush:(timerId:string)=>request<void>(`/reminders/push/rest-timers/${timerId}`,{method:"DELETE"}),
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
export type WorkoutExercise = { id:string; exerciseId:string; name:string; muscleGroup?:string; equipment?:string; order:number; targetSets?:number; targetRepsMin?:number; targetRepsMax?:number; targetRir?:number; targetRpe?:number; targetRestSeconds?:number; instructions?:string; completed:boolean; painReported:boolean; painArea?:string;painIntensity?:number;notes?:string;activityName?:string;activityMinutes?:number;activityCalories?:number; sets:WorkoutSet[] };
export type WorkoutSession = { header:{id:string;name:string;plannedDate:string;status:string;startedAt:string;completedAt?:string;durationSeconds?:number;globalRpe?:number;energyLevel?:number;pumpLevel?:number;painLevel?:number;difficultyLevel?:number;notes?:string;clientExternalId:string;workoutPlanVersion?:number;pausedAt?:string;pausedSeconds:number}; exercises:WorkoutExercise[]; metrics:{exercises:number;sets:number;repetitions:number;volume:number;maxPain:number;personalRecords:number} };
export type TodayWorkout = { planId:string;planName:string;planVersion:number;dayId:string;sessionName:string;dayName:string;weekNumber:number;dayNumber:number;estimatedMinutes:number;exerciseCount:number;exercises:Array<{exerciseId:string;name:string;muscleGroup?:string;sets:number;repsMin:number;repsMax:number;targetRir?:number;restSeconds?:number}> };
export type TodayWorkoutAdjustment = { status:"MOVED"|"SKIPPED";scheduledDate?:string;sessionName:string;reason?:string };
export type TodayWorkoutStatus = { workout:TodayWorkout|null;adjustment?:TodayWorkoutAdjustment;workouts?:TodayWorkout[] };
export type WorkoutDayAdjustment = { dayId:string;originalDate:string;scheduledDate?:string;status:"MOVED"|"SKIPPED";reason?:string;sessionName:string;dayNumber:number };
export type WorkoutSummary = {id:string;name:string;date:string;status:string;workoutPlanDayId?:string;durationSeconds?:number;globalRpe?:number;exercises:number;sets:number;volume:number;activityCalories?:number};
export type WorkoutPlan = {id:string;name:string;version:number;status:string;validFrom?:string;validUntil?:string};
export type PlannedWorkoutExercise = {weekNumber:number;dayNumber:number;dayName?:string;sessionName:string;order:number;exercise:string;muscleGroup?:string;equipment?:string;sets:number;repsMin:number;repsMax:number;targetRir?:number;targetRpe?:number;restSeconds?:number;tempo?:string;warmupRequired:boolean;supersetGroup?:string;alternativeExerciseCode?:string;instructions?:string;notes?:string;dayId:string};
async function rescheduleWorkout<T>(path:string,body:Record<string,unknown>):Promise<T>{try{return await request<T>(path,{method:"POST",body:JSON.stringify(body)})}catch(error){if(error instanceof Error&&error.message.includes("otro entrenamiento previsto")&&confirm(`${error.message}\n\n¿Quieres moverlo igualmente? Tendrás dos entrenamientos asignados ese día.`))return request<T>(path,{method:"POST",body:JSON.stringify({...body,force:true})});throw error}}
export const workoutApi = {
  today:()=>request<TodayWorkoutStatus>("/workouts/today"),
  byDate:(date:string)=>request<TodayWorkout[]>(`/workouts?date=${date}`),
  adjustments:(planId:string)=>request<WorkoutDayAdjustment[]>(`/workout-plans/${planId}/adjustments`),
  rescheduleToday:(date:string)=>rescheduleWorkout<TodayWorkout>("/workouts/today/reschedule",{date}),
  skipToday:(reason?:string)=>request<void>("/workouts/today/skip",{method:"POST",body:JSON.stringify({reason})}),
  rescheduleDay:(dayId:string,originalDate:string,targetDate:string)=>rescheduleWorkout<void>(`/workout-plan-days/${dayId}/reschedule`,{originalDate,targetDate}),
  skipDay:(dayId:string,originalDate:string,reason?:string)=>request<void>(`/workout-plan-days/${dayId}/skip`,{method:"POST",body:JSON.stringify({originalDate,reason})}),
  active:()=>request<WorkoutSession|null>("/workout-sessions/active"),
  history:(page=0,size=30)=>request<WorkoutSummary[]>(`/workout-sessions?page=${page}&size=${size}`),
  plans:()=>request<WorkoutPlan[]>("/workout-plans"),
  planDetails:(id:string)=>request<PlannedWorkoutExercise[]>(`/workout-plans/${id}/details`),
  deletePlan:(id:string)=>request<void>(`/workout-plans/${id}`,{method:"DELETE"}),
  one:(id:string)=>request<WorkoutSession>(`/workout-sessions/${id}`),
  deleteSession:(id:string)=>request<void>(`/workout-sessions/${id}`,{method:"DELETE"}),
  start:(body:{workoutPlanDayId?:string;name?:string;plannedDate?:string;clientExternalId:string})=>request<WorkoutSession>("/workout-sessions",{method:"POST",body:JSON.stringify(body)}),
  pause:(id:string)=>request<WorkoutSession>(`/workout-sessions/${id}/pause`,{method:"POST"}),
  resume:(id:string)=>request<WorkoutSession>(`/workout-sessions/${id}/resume`,{method:"POST"}),
  complete:(id:string,body:object)=>request<WorkoutSession>(`/workout-sessions/${id}/complete`,{method:"POST",body:JSON.stringify(body)}),
  updateDuration:(id:string,seconds:number)=>request<WorkoutSession>(`/workout-sessions/${id}/duration`,{method:"PATCH",body:JSON.stringify({seconds})}),
  abandon:(id:string,reason?:string)=>request<WorkoutSession>(`/workout-sessions/${id}/abandon`,{method:"POST",body:JSON.stringify({reason})}),
  addExercise:(session:string,body:object)=>request<WorkoutExercise>(`/workout-sessions/${session}/exercises`,{method:"POST",body:JSON.stringify(body)}),
  addSet:(session:string,exercise:string,body:object)=>request<WorkoutSet>(`/workout-sessions/${session}/exercises/${exercise}/sets`,{method:"POST",body:JSON.stringify(body)}),
  updateSet:(session:string,exercise:string,set:string,body:object)=>request<WorkoutSet>(`/workout-sessions/${session}/exercises/${exercise}/sets/${set}`,{method:"PATCH",body:JSON.stringify(body)}),
  deleteSet:(session:string,exercise:string,set:string)=>request<void>(`/workout-sessions/${session}/exercises/${exercise}/sets/${set}`,{method:"DELETE"}),
  finishExercise:(session:string,exercise:string)=>request<WorkoutExercise>(`/workout-sessions/${session}/exercises/${exercise}/complete`,{method:"POST"}),
  recordActivity:(session:string,exercise:string,body:{name:string;minutes:number;calories:number;notes?:string})=>request<WorkoutExercise>(`/workout-sessions/${session}/exercises/${exercise}/activity`,{method:"POST",body:JSON.stringify(body)}),
  pain:(session:string,exercise:string,body:object)=>request<WorkoutExercise>(`/workout-sessions/${session}/exercises/${exercise}/pain`,{method:"PATCH",body:JSON.stringify(body)}),
  exercises:()=>request<Array<{id:string;name:string;muscleGroup?:string;equipment?:string}>>("/exercises"),
  createExercise:(body:{name:string;muscleGroup:string})=>request<{id:string;name:string;muscleGroup?:string;equipment?:string}>("/exercises",{method:"POST",body:JSON.stringify(body)}),
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
export type TodayMeal = { planned_meal_id:string;plan_id:string;plan_name:string;version:number;day_name:string;meal_type:string;meal_name:string;recipe:string;calories:number;protein:number;carbohydrates:number;fat:number;planned_calories:number;planned_protein:number;planned_carbohydrates:number;planned_fat:number;portion_multiplier:number;status:MealStatus;meal_date?:string;consumed_meal_id?:string;custom_name?:string;actual_portion?:string;notes?:string;adherence_percent?:number;option_group?:string;option_code?:string;option_label?:string;completed_at?:string };
export type MealOption={planned_meal_id:string;option_code:string;option_label:string;recipe:string;calories:number;protein:number;carbohydrates:number;fat:number};
export type NutritionDashboard={target:Partial<Record<"calories"|"protein"|"carbohydrates"|"fat"|"fiber",number>>;planned:Record<"calories"|"protein"|"carbohydrates"|"fat",number>;consumed:Record<"calories"|"protein"|"carbohydrates"|"fat",number>;week:Array<{date:string;calories:number}>};
export type AdherenceDashboard={days:number;meals:{completed:number;substituted:number;partial:number;skipped:number;missing:number;expected:number;score:number};workouts:{completed:number;partial:number;abandoned:number;missing:number;expected:number;score:number};patterns:Array<{day_number:number;incidents:number}>;weekly:Array<{week:string;meal_score?:number;workout_score?:number}>;currentWeek?:{expected:number;completed:number;score:number;complete:boolean};workoutReasons:Array<{reason:string;incidents:number}>};
export type ConsumedMeal={id:string;meal_date:string;meal_type:string;status:MealStatus;name?:string;portion?:string;calories?:number;protein?:number;carbohydrates?:number;fat?:number;notes?:string;adherence_percent?:number;deviation_reason?:string;completed_at?:string;planned_meal?:string;planned_recipe?:string};
export type TravelMode={id:string;title:string;start_date:string;end_date:string;status:string;general_guidance?:string;day_count:number;exclude_from_adherence:boolean;exclude_from_shopping:boolean};
export type TravelDay={date:string;plan:string;guidance:string};
export type TravelToday={id?:string;title?:string;start_date?:string;end_date?:string;general_guidance?:string;plan_label?:string;guidance?:string;travel_date?:string};
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
  today:(date?:string)=>request<TodayMeal[]>(`/nutrition/today${date?`?date=${date}`:""}`),
  dashboard:()=>request<NutritionDashboard>("/nutrition/dashboard"),
  adherence:(days=28)=>request<AdherenceDashboard>(`/nutrition/adherence?days=${days}`),
  consumedMeals:(from="2000-01-01")=>request<ConsumedMeal[]>(`/nutrition/consumed-meals?from=${from}`),
  travelModes:()=>request<TravelMode[]>("/nutrition/travel-modes"),
  travelToday:(date?:string)=>request<TravelToday>(`/nutrition/travel-modes/today${date?`?date=${date}`:""}`),
  travelCalendar:(from:string,to:string)=>request<Array<{id:string;title:string;start_date:string;end_date:string;travel_date:string;plan_label:string;guidance:string}>>(`/nutrition/travel-modes/calendar?from=${from}&to=${to}`),
  createTravel:(data:{title:string;startDate:string;endDate:string})=>request<{id:string;prompt:string}>("/nutrition/travel-modes",{method:"POST",body:JSON.stringify(data)}),
  travelPrompt:(id:string)=>request<{prompt:string}>(`/nutrition/travel-modes/${id}/prompt`),
  previewTravel:(id:string,content:string)=>request<{days:TravelDay[];confirmable:boolean;expectedDays:number}>(`/nutrition/travel-modes/${id}/preview`,{method:"POST",body:JSON.stringify({content})}),
  importTravel:(id:string,content:string)=>request<{id:string;status:string;days:number}>(`/nutrition/travel-modes/${id}/import`,{method:"POST",body:JSON.stringify({content})}),
  deleteTravel:(id:string)=>request<void>(`/nutrition/travel-modes/${id}`,{method:"DELETE"}),
  targets:()=>request<Array<Record<string,number|string>>>("/nutrition/targets"),
  saveTarget:(data:{validFrom:string;calories:number;protein?:number;carbohydrates?:number;fat?:number;fiber?:number})=>request<void>("/nutrition/targets",{method:"PUT",body:JSON.stringify(data)}),
  completeToday:(id:string,date?:string)=>request<Record<string,unknown>>(`/nutrition/today/${id}/complete${date?`?date=${date}`:""}`,{method:"POST"}),
  undoToday:(id:string,date?:string)=>request<void>(`/nutrition/today/${id}/completion${date?`?date=${date}`:""}`,{method:"DELETE"}),
  skipToday:(id:string,notes?:string,date?:string)=>request<Record<string,unknown>>(`/nutrition/today/${id}/skip`,{method:"POST",body:JSON.stringify({notes,date})}),
  substituteToday:(id:string,data:Omit<MealInput,"mealType">)=>request<Record<string,unknown>>(`/nutrition/today/${id}/substitute`,{method:"POST",body:JSON.stringify(data)}),
  additionalToday:(id:string,data:MealInput)=>request<Record<string,unknown>>(`/nutrition/today/${id}/additional`,{method:"POST",body:JSON.stringify(data)}),
  partialToday:(id:string,data:{percent:number;reason?:string;portion?:string;notes?:string;date?:string})=>request<Record<string,unknown>>(`/nutrition/today/${id}/partial`,{method:"POST",body:JSON.stringify(data)}),
  mealOptions:(id:string,userId?:string)=>request<MealOption[]>(`/nutrition/today/${id}/options${userId?`?userId=${userId}`:""}`),
  selectMealOption:(id:string,optionCode:string,date:string,userId?:string)=>request<void>(`/nutrition/today/${id}/option`,{method:"POST",body:JSON.stringify({optionCode,date,userId})}),
  customMeal:(data:MealInput)=>request<Record<string,unknown>>("/nutrition/meals/custom",{method:"POST",body:JSON.stringify(data)}),
  updateConsumed:(id:string,data:MealInput)=>request<Record<string,unknown>>(`/nutrition/consumed-meals/${id}`,{method:"PATCH",body:JSON.stringify(data)}),
  deleteConsumed:(id:string)=>request<void>(`/nutrition/consumed-meals/${id}`,{method:"DELETE"}),
  recipes: () =>
    request<
      Array<{ id: string; code: string; name: string; servings: number }>
    >("/nutrition/recipes"),
  plans: () =>
    request<
      Array<{ id: string; name: string; version: number; status: string; valid_from?: string; valid_until?: string }>
    >("/nutrition/plans"),
  week: (id: string) =>
    request<Array<Record<string, unknown>>>(`/nutrition/plans/${id}/week`),
  planMealOptions:(id:string)=>request<Array<Record<string,unknown>>>(`/nutrition/plans/${id}/meal-options`),
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
  deleteShoppingItem:(id:string)=>request<void>(`/nutrition/shopping-items/${id}`,{method:"DELETE"}),
  shoppingQuantity: (id: string, quantity: number) =>
    request<void>(`/nutrition/shopping-items/${id}/quantity`, {method:"PATCH",body:JSON.stringify({quantity})}),
  resetShopping:(id:string)=>request<void>(`/nutrition/shopping-lists/${id}`,{method:"DELETE"}),
  pantry:()=>request<Array<{ingredient_id:string;name:string;category:string;quantity:number;unit:string}>>("/nutrition/pantry"),
  ingredientSuggestions:(query:string)=>request<Array<{name:string;category:string;unit:string;source:"CATALOG"|"SUGGESTED"}>>(`/nutrition/ingredient-suggestions?q=${encodeURIComponent(query)}`),
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
