import { useEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";
import {
  Activity,
  Apple,
  BarChart3,
  ChevronDown,
  Copy,
  Download,
  Dumbbell,
  FileUp,
  Home,
  CircleUserRound,
  Droplets,
  LogOut,
  Plus,
  Scale,
  Sparkles,
  Target,
  Trash2,
  X,
} from "lucide-react";
import {
  api,
  API_BASE,
  Entry,
  EntryType,
  ImportPreview,
  trainingApi,
  nutritionApi,
  TodayMeal,
  NutritionDashboard,
  TodayWorkout,
  workoutApi,
  User,
} from "./api";
import { NutritionHub } from "./NutritionHub";
import { BodyProgress } from "./BodyProgress";
import { WorkRoutinePanel } from "./WorkRoutinePanel";
import { WorkoutHub } from "./WorkoutHub";
import { clearWorkoutOffline } from "./workoutOffline";
import { MealFlow } from "./MealFlow";
import { CycleTracker } from "./CycleTracker";

const meta: Record<
  EntryType,
  { label: string; icon: typeof Activity; unit: string; color: string }
> = {
  WORKOUT: { label: "Entreno", icon: Dumbbell, unit: "min", color: "lime" },
  MEAL: { label: "Comida", icon: Apple, unit: "kcal", color: "orange" },
  WEIGHT: { label: "Peso", icon: Scale, unit: "kg", color: "blue" },
  MEASUREMENT: {
    label: "Medida",
    icon: BarChart3,
    unit: "cm",
    color: "purple",
  },
  GOAL: { label: "Objetivo", icon: Target, unit: "%", color: "pink" },
};
type ProfilePreferences={primary_goal?:string;experience_level?:string;activity_level?:string;height_cm?:number;training_days?:number;limitations?:string;biological_sex?:string;avatar_url?:string;reminder_email_enabled?:boolean;reminder_frequency?:string;last_summary_sent_at?:string};

export function App() {
  const [user, setUser] = useState<User | null>(() => {
    try {
      return JSON.parse(localStorage.getItem("anura-user") || "null");
    } catch {
      return null;
    }
  });
  const [entries, setEntries] = useState<Entry[]>([]);
  const [tab, setTab] = useState<"HOME" | EntryType | "CYCLE">("HOME");
  const [modal, setModal] = useState(false);
  const [loading, setLoading] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [nutritionOpen, setNutritionOpen] = useState(false);
  const [workoutOpen, setWorkoutOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [profilePreferences,setProfilePreferences]=useState<ProfilePreferences>({});
  const [todayMeals, setTodayMeals] = useState<TodayMeal[]>([]);
  const [nutritionDashboard,setNutritionDashboard]=useState<NutritionDashboard|null>(null);
  const [todayWorkout, setTodayWorkout] = useState<TodayWorkout | null>(null);
  const [todayWorkoutDone, setTodayWorkoutDone] = useState(false);
  const [dailyLoading, setDailyLoading] = useState(false);
  const [mealsExpanded,setMealsExpanded]=useState(false);
  const [mealFlowOpen, setMealFlowOpen] = useState(false);
  const [selectedPlannedMeal,setSelectedPlannedMeal]=useState<string|null>(null);
  const [editingMeal, setEditingMeal] = useState<Entry | null>(null);
  const load = () => {
    if (user)
      void api
        .entries()
        .then(setEntries)
        .catch(() => logout());
  };
  useEffect(() => {
    load();
  }, [user]);
  useEffect(() => {
    if (!user) return;
    void Promise.all([nutritionApi.today().catch(() => []), workoutApi.today().catch(() => null), workoutApi.history().catch(() => [])]).then(([meals, workout, sessions]) => {
      setTodayMeals(meals);
      setTodayWorkout(workout);
      setTodayWorkoutDone(sessions.some((session) => session.date === new Date().toISOString().slice(0,10) && session.status === "COMPLETED"));
    });
    void nutritionApi.dashboard().then(setNutritionDashboard).catch(()=>setNutritionDashboard(null));
    void api.profilePreferences().then(setProfilePreferences).catch(()=>setProfilePreferences({}));
  }, [user]);
  function logout() {
    localStorage.removeItem("anura-token");
    localStorage.removeItem("anura-user");
    void clearWorkoutOffline();
    setUser(null);
  }
  if (!user)
    return (
      <Auth
        onAuth={(u, t) => {
          localStorage.setItem("anura-token", t);
          localStorage.setItem("anura-user", JSON.stringify(u));
          setUser(u);
        }}
      />
    );
  const visible =
    tab === "HOME" ? entries : entries.filter((e) => e.type === tab);
  const today = new Date().toISOString().slice(0, 10);
  const todayItems = entries.filter((e) => e.entryDate === today);
  const plannedToday=todayMeals.length+(todayWorkout?1:0);
  const completedToday=todayMeals.filter(meal=>meal.status!=="PENDING").length+(todayWorkoutDone||todayItems.some(item=>item.type==="WORKOUT")?1:0);
  const dailyPercent=plannedToday?Math.round(completedToday/plannedToday*100):0;
  return (
    <main className="shell">
      <header>
        <div className="brand">
          <img src="/anura-mascot.png" alt="" /> ANURA
        </div>
        <div className="header-actions">
          <button className="icon-btn" onClick={() => setAccountOpen(true)} aria-label="Mi perfil" title="Mi perfil">
            {profilePreferences.avatar_url?<img className="header-avatar" src={profilePreferences.avatar_url} alt={user.displayName}/>:<CircleUserRound size={19} />}
          </button>
          <button className="icon-btn" onClick={logout} aria-label="Cerrar sesión">
            <LogOut size={19} />
          </button>
        </div>
      </header>
      <section className="content">
        {tab === "HOME" ? (
          <>
            <section className="home-hero">
              <div className="home-hero-copy"><p><Sparkles/> HOLA, {user.displayName.toUpperCase()}</p><h1>Haz que hoy<br/><em>cuente.</em></h1><span>{new Intl.DateTimeFormat("es",{weekday:"long",day:"numeric",month:"long"}).format(new Date())}</span><div className="home-streak"><b>{completedToday}/{plannedToday||"—"}</b><small>acciones completadas hoy</small></div></div>
              <div className="home-hero-visual"><img src="/anura-mascot.png" alt="Mascota de ANURA"/><div className="home-progress-ring" style={{"--home-progress":`${dailyPercent*3.6}deg`} as CSSProperties}><span><b>{dailyPercent}%</b><small>HOY</small></span></div></div>
            </section>
            <div className="daily-plan-head"><span>PLAN DE HOY</span></div>
            <div className="daily-plan-grid">
              <button className="daily-focus workout" onClick={() => setWorkoutOpen(true)}>
                <span className="daily-focus-icon"><Dumbbell /></span>
                <span><small>ENTRENAMIENTO</small><strong>{todayWorkout?.sessionName || "Sesión libre"}</strong><b>{todayWorkout ? `${todayWorkout.exerciseCount} ejercicios · ~${todayWorkout.estimatedMinutes || 45} min` : "No hay plan asignado hoy"}</b></span>
                <em>{todayWorkoutDone || todayItems.some((e) => e.type === "WORKOUT") ? "Hecho" : "Pendiente"}</em>
              </button>
              <div className="daily-focus nutrition">
                <button className="daily-focus-main" onClick={() => setMealsExpanded(value=>!value)} aria-expanded={mealsExpanded}>
                  <span className="daily-focus-icon"><Apple /></span>
                  <span><small>COMIDAS</small><strong>{todayMeals.length ? `${todayMeals.filter(m => m.status === "PENDING").length} pendientes` : "Sin plan para hoy"}</strong><b>{nutritionDashboard?`${Number(nutritionDashboard.consumed.calories||0).toFixed(0)} de ${Number(nutritionDashboard.target.calories||nutritionDashboard.planned.calories||0).toFixed(0)} kcal consumidas`:todayMeals.length ? `${todayMeals.reduce((sum,m) => sum + Number(m.calories || 0),0).toFixed(0)} kcal planificadas` : "Añade una comida o abre tus planes"}</b></span>
                  <ChevronDown className={`daily-expand-icon ${mealsExpanded?"open":""}`}/>
                </button>
                <div className="daily-meal-actions"><button onClick={()=>setMealsExpanded(value=>!value)}>{mealsExpanded?"Ocultar comidas":"Desplegar comidas"}<ChevronDown className={mealsExpanded?"open":""}/></button><button onClick={()=>setNutritionOpen(true)}>Ver plan</button></div>
                {mealsExpanded&&todayMeals.length > 0 && <div className="today-meals-mini">{todayMeals.map(meal => <div key={meal.planned_meal_id} className={meal.status !== "PENDING" ? "completed" : ""}><button onClick={() => setMealFlowOpen(true)}><span><b>{meal.custom_name||meal.meal_name}</b><small>{meal.status === "SKIPPED" ? "Saltada" : meal.status === "SUBSTITUTED" ? "Sustituida" : `${meal.recipe} · ${Number(meal.calories || 0).toFixed(0)} kcal`}</small></span></button><button className="meal-complete" disabled={dailyLoading} title={meal.status==="PENDING"?"Marcar como hecha":"Deshacer registro"} onClick={async () => {setDailyLoading(true);try{if(meal.status==="PENDING")await nutritionApi.completeToday(meal.planned_meal_id);else await nutritionApi.undoToday(meal.planned_meal_id);setTodayMeals(await nutritionApi.today());setNutritionDashboard(await nutritionApi.dashboard());load();}finally{setDailyLoading(false)}}}>{meal.status !== "PENDING" ? "Deshacer" : "Completar"}</button></div>)}</div>}
                {mealsExpanded&&<button className="daily-add-meal" onClick={() => {setEditingMeal(null);setMealFlowOpen(true)}}><Plus/>Revisar dieta o añadir comida</button>}
              </div>
            </div>
            <div className="score">
              <div>
                <small>RITMO DE HOY</small>
                <strong>{dailyPercent}%</strong>
              </div>
              <div
                className="ring"
                style={
                  {
                    "--score": `${dailyPercent * 3.6}deg`,
                  } as CSSProperties
                }
              >
                <span>{completedToday}</span>
              </div>
            </div>
            <h2>Otros seguimientos</h2>
            <div className="quick-grid">
              {(["WEIGHT", "GOAL"] as EntryType[]).map(
                (type) => (
                  <Quick
                    key={type}
                    type={type}
                    entries={todayItems}
                    onClick={() => setTab(type)}
                  />
                ),
              )}
            </div>
            <div className="plan-tools"><span><b>Gestionar planes</b><small>Plantillas, CSV y nuevas versiones</small></span><button onClick={() => setImportOpen(true)}><FileUp />Importar entreno</button><button onClick={() => setNutritionOpen(true)}><Apple />Dietas y hogar</button></div>
            <h2>Actividad reciente</h2>
          </>
        ) : (
          <div className="section-title">
            <button onClick={() => setTab("HOME")}>← Inicio</button>
            <p>{tab === "CYCLE" ? "Ciclo" : meta[tab].label}</p>
            <h1>{tab === "CYCLE" ? "Ciclo menstrual" : `${meta[tab].label}s`}</h1>
          </div>
        )}
        {tab === "WEIGHT" && <BodyProgress />}
        {tab === "CYCLE" && <CycleTracker />}
        {tab === "GOAL" && <GoalVision goals={entries.filter(entry=>entry.type==="GOAL")} onAdd={()=>setModal(true)}/>}
        {tab === "WORKOUT" && (
          <button className="workout-launch" onClick={() => setWorkoutOpen(true)}>
            <span><small>ENTRENAMIENTO DE HOY</small><strong>Entrenar ahora</strong><b>Plan, series, descanso y progreso</b></span>
            <Dumbbell />
          </button>
        )}
        {tab === "WEIGHT" && entries.some((entry) => entry.type === "WEIGHT") && <h2 className="subsection-title">Registros anteriores</h2>}
        {tab !== "CYCLE" && (tab !== "WEIGHT" || visible.length > 0) && (
          <EntryList
            entries={visible.slice(0, 12)}
            onEdit={(entry) => {if(entry.type === "MEAL"){setEditingMeal(entry);setMealFlowOpen(true)}}}
            onDelete={async (id) => {
              await api.remove(id);
              load();
            }}
          />
        )}
        {tab === "WORKOUT" && (
          <button className="import-card" onClick={() => setImportOpen(true)}>
            <FileUp />
            <span>
              <strong>Importar planificación</strong>
              <small>Plantilla entrenamiento v1</small>
            </span>
            <b>→</b>
          </button>
        )}
        {tab === "MEAL" && (
          <button
            className="import-card"
            onClick={() => setNutritionOpen(true)}
          >
            <Apple />
            <span>
              <strong>Nutrición</strong>
              <small>Hoy, planes, cocina y compra</small>
            </span>
            <b>→</b>
          </button>
        )}
      </section>
      {tab !== "WEIGHT" && tab !== "CYCLE" && <button className="fab" onClick={() => tab === "MEAL" ? (setEditingMeal(null),setMealFlowOpen(true)) : setModal(true)}>
        <Plus />
      </button>}
      <nav>
        {[
          { id: "HOME" as const, icon: Home, label: "Inicio" },
          { id: "WORKOUT" as const, icon: Dumbbell, label: "Entreno" },
          { id: "MEAL" as const, icon: Apple, label: "Nutrición" },
          { id: "WEIGHT" as const, icon: Activity, label: "Evolución" },
          ...(profilePreferences.biological_sex === "FEMALE" ? [{ id: "CYCLE" as const, icon: Droplets, label: "Ciclo" }] : []),
        ].map((n) => (
          <button
            className={tab === n.id ? "active" : ""}
            onClick={() =>
              n.id === "WORKOUT"
                ? setWorkoutOpen(true)
                : n.id === "MEAL"
                  ? setNutritionOpen(true)
                  : setTab(n.id)
            }
            key={n.id}
          >
            <n.icon />
            <span>{n.label}</span>
          </button>
        ))}
      </nav>
      {modal && (
        <EntryModal
          initialType={tab === "GOAL" ? "GOAL" : tab === "MEAL" ? "MEAL" : "WORKOUT"}
          busy={loading}
          onClose={() => setModal(false)}
          onMealSelected={() => {
            setModal(false);
            setEditingMeal(null);
            setMealFlowOpen(true);
          }}
          onSave={async (e) => {
            setLoading(true);
            try {
              await api.create(e);
              setModal(false);
              load();
            } finally {
              setLoading(false);
            }
          }}
        />
      )}
      {importOpen && <TrainingImport onClose={() => setImportOpen(false)} />}
      {nutritionOpen && (
        <NutritionHub onClose={() => setNutritionOpen(false)} onRegisterMeal={(mealId)=>{setSelectedPlannedMeal(mealId);setNutritionOpen(false);setEditingMeal(null);setMealFlowOpen(true)}} />
      )}
      {workoutOpen && (
        <WorkoutHub
          onImport={() => {
            setWorkoutOpen(false);
            setImportOpen(true);
          }}
          onClose={() => {
            setWorkoutOpen(false);
            load();
            void workoutApi.history().then((sessions) =>
              setTodayWorkoutDone(
                sessions.some(
                  (session) =>
                    session.date === new Date().toISOString().slice(0, 10) &&
                    session.status === "COMPLETED",
                ),
              ),
            );
          }}
        />
      )}
      {accountOpen && <AccountModal user={user} onPreferences={preferences=>{setProfilePreferences(preferences);if(preferences.biological_sex!=="FEMALE"&&tab==="CYCLE")setTab("HOME")}} onAvatar={avatar_url=>setProfilePreferences(current=>({...current,avatar_url}))} onClose={() => setAccountOpen(false)} />}
      {mealFlowOpen && <MealFlow meals={todayMeals} editing={editingMeal} initialMealId={selectedPlannedMeal} onClose={() => {setMealFlowOpen(false);setSelectedPlannedMeal(null)}} onDone={() => {setMealFlowOpen(false);setEditingMeal(null);setSelectedPlannedMeal(null);load();void nutritionApi.today().then(setTodayMeals)}}/>}
    </main>
  );
}

function GoalVision({goals,onAdd}:{goals:Entry[];onAdd:()=>void}){
 const active=goals[0];
 return <section className="goal-vision"><div className="goal-frog" aria-hidden="true"/><div><small>TU DIRECCIÓN</small><h2>{active?.title||"Define hacia dónde vas"}</h2><p>{active?.details||"Bajar grasa, ganar músculo o mejorar hábitos: tu nutrición, entrenamientos y evolución trabajan sobre una misma meta."}</p><div className="goal-pill-row"><span>Bajar grasa</span><span>Ganar músculo</span><span>Mejorar hábitos</span></div><button onClick={onAdd}>{active?"Actualizar objetivo":"Crear mi objetivo"}</button></div>{active&&<strong>{Number(active.value||0).toFixed(0)}<small>% progreso</small></strong>}</section>
}
function goalLabel(value?:string){return ({LOSE_FAT:"Objetivo · Bajar grasa",GAIN_MUSCLE:"Objetivo · Ganar músculo",BODY_RECOMPOSITION:"Objetivo · Recomposición corporal",HEALTH:"Objetivo · Salud y hábitos"} as Record<string,string>)[value||""]||"Completa tus datos iniciales"}

function TrainingImport({ onClose }: { onClose: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState("");
  async function validate() {
    if (!file) return;
    setBusy(true);
    setError("");
    try {
      setPreview(await trainingApi.preview(file));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo validar el archivo");
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="overlay">
      <section className="modal import-modal">
        <div className="modal-head">
          <div>
            <small>ENTRENAMIENTO · CSV V1</small>
            <h2>Importar planificación</h2>
          </div>
          <button onClick={onClose}>
            <X />
          </button>
        </div>
        <div className="csv-identity"><small>IDENTIFICADOR DE ESTA CUENTA</small><b>{JSON.parse(localStorage.getItem("anura-user")||"{}").email}</b><span>ChatGPT debe repetir este email en <code>user_identifier</code>.</span></div>
        {done ? (
          <div className="import-success">
            <Target />
            <h3>Plan importado</h3>
            <p>Ya está disponible en tu histórico.</p>
            <button className="primary" onClick={onClose}>
              Terminar
            </button>
          </div>
        ) : (
          <>
            <a
              className="template-link"
              href={`${API_BASE}/import-schemas/training-plan/v1/template`}
            >
              <Download /> Descargar plantilla oficial
            </a>
            <label className="file-drop">
              <FileUp />
              <strong>{file ? file.name : "Selecciona el CSV"}</strong>
              <small>UTF-8 · Separador ; · Máximo 1 MB</small>
              <input
                type="file"
                accept=".csv,text/csv"
                onChange={(e) => {
                  setFile(e.target.files?.[0] || null);
                  setPreview(null);
                }}
              />
            </label>
            {error && <div className="error">{error}</div>}
            {preview && (
              <div className="preview-card">
                <span className={preview.confirmable ? "valid" : "invalid"}>
                  {preview.confirmable
                    ? "Archivo válido"
                    : "Requiere correcciones"}
                </span>
                <h3>
                  {preview.planName || "Sin nombre"} · v{preview.version || "?"}
                </h3>
                <div className="preview-stats">
                  <b>
                    {preview.weeks}
                    <small>semanas</small>
                  </b>
                  <b>
                    {preview.days}
                    <small>días</small>
                  </b>
                  <b>
                    {preview.exercises}
                    <small>ejercicios</small>
                  </b>
                </div>
                {preview.issues.map((i, n) => (
                  <details key={n}>
                    <summary>
                      Fila {i.row || "—"} · {i.column || "archivo"}
                    </summary>
                    <p>{i.message}</p>
                  </details>
                ))}
              </div>
            )}
            {!preview ? (
              <button
                className="primary"
                disabled={!file || busy}
                onClick={validate}
              >
                {busy ? "Validando..." : "Validar y previsualizar"}
              </button>
            ) : (
              <button
                className="primary"
                disabled={!preview.confirmable || busy}
                onClick={async () => {
                  setBusy(true);
                  try {
                    const imported = await trainingApi.confirm(preview.importJobId);
                    await trainingApi.activate(imported.planId);
                    setDone(true);
                  } catch (cause) {
                    setError(cause instanceof Error ? cause.message : "No se pudo confirmar la importación");
                  } finally {
                    setBusy(false);
                  }
                }}
              >
                {busy ? "Importando..." : "Confirmar importación"}
              </button>
            )}
          </>
        )}
      </section>
    </div>
  );
}

function Quick({
  type,
  entries,
  onClick,
}: {
  type: EntryType;
  entries: Entry[];
  onClick: () => void;
}) {
  const m = meta[type],
    Icon = m.icon,
    item = entries.find((e) => e.type === type);
  return (
    <button className={`quick ${m.color}`} onClick={onClick}>
      <Icon />
      <span>{m.label}</span>
      <strong>
        {item
          ? item.value
            ? `${item.value} ${item.unit || m.unit}`
            : "Hecho"
          : "Pendiente"}
      </strong>
    </button>
  );
}
function EntryList({
  entries,
  onDelete,
  onEdit,
}: {
  entries: Entry[];
  onDelete: (id: string) => void;
  onEdit: (entry: Entry) => void;
}) {
  const [expanded, setExpanded] = useState<string | null>(null);
  if (!entries.length)
    return (
      <div className="empty">
        Nada registrado todavía.
        <br />
        Pulsa + para empezar.
      </div>
    );
  return (
    <div className="entries">
      {entries.map((e) => {
        const m = meta[e.type],
          Icon = m.icon;
        return (
          <article
            key={e.id}
            className={`entry-card ${expanded === e.id ? "expanded" : ""}`}
          >
            <div className={`entry-icon ${m.color}`}>
              <Icon />
            </div>
            <button
              className="entry-content"
              onClick={() => setExpanded(expanded === e.id ? null : e.id)}
              aria-expanded={expanded === e.id}
            >
              <small>
                {m.label} ·{" "}
                {new Date(`${e.entryDate}T12:00`).toLocaleDateString("es")}
              </small>
              <h3>{e.title}</h3>
              <p className="entry-preview">
                {e.details || e.notes || "Registro completado"}
              </p>
              <span className="entry-action">
                {expanded === e.id ? "Ocultar detalle" : e.type === "MEAL" ? "Ver comida" : "Ver detalle"}
                <ChevronDown size={15} />
              </span>
            </button>
            {e.value != null && (
              <strong className="entry-value">
                {e.value}
                <small>{e.unit || m.unit}</small>
              </strong>
            )}
            <button className="entry-delete" onClick={() => onDelete(e.id)} aria-label="Eliminar">
              <X />
            </button>
            <div className="entry-detail">
              <div>
                <small>DETALLE</small>
                <p>{e.details || e.notes || "Registro completado sin notas adicionales."}</p>
              </div>
              <span className="status-pill">Completado</span>
              {e.type === "MEAL" && <button className="entry-edit" onClick={() => onEdit(e)}>Editar comida</button>}
            </div>
          </article>
        );
      })}
    </div>
  );
}

function EntryModal({
  initialType,
  onClose,
  onSave,
  onMealSelected,
  busy,
}: {
  initialType: EntryType;
  onClose: () => void;
  onSave: (e: Omit<Entry, "id">) => void;
  onMealSelected: () => void;
  busy: boolean;
}) {
  const [type, setType] = useState<EntryType>(initialType);
  const m = meta[type];
  return (
    <div className="overlay">
      <form
        className="modal"
        onSubmit={(e) => {
          e.preventDefault();
          const f = new FormData(e.currentTarget);
          onSave({
            type,
            title: String(f.get("title")),
            entryDate: String(f.get("date")),
            value: f.get("value") ? Number(f.get("value")) : undefined,
            unit: String(f.get("unit") || m.unit),
            details: String(f.get("details") || ""),
            notes: "",
            completed: true,
          });
        }}
      >
        <div className="modal-head">
          <div>
            <small>NUEVO REGISTRO</small>
            <h2>Añadir al día</h2>
          </div>
          <button type="button" onClick={onClose}>
            <X />
          </button>
        </div>
        <div className="types">
          {(Object.keys(meta) as EntryType[]).filter(t => t !== "WEIGHT" && t !== "MEASUREMENT").map((t) => {
            const I = meta[t].icon;
            return (
              <button
                type="button"
                className={type === t ? "selected" : ""}
                onClick={() => t === "MEAL" ? onMealSelected() : setType(t)}
                key={t}
              >
                <I />
                <span>{meta[t].label}</span>
              </button>
            );
          })}
        </div>
        <label>
          Título
          <input
            required
            name="title"
            placeholder={
              type === "WORKOUT"
                ? "Pierna y core"
                : type === "MEAL"
                  ? "Desayuno"
                  : type === "GOAL"
                    ? "Ej. Bajar grasa y ganar músculo"
                    : "Registro"
            }
          />
        </label>
        <div className="row">
          <label>
            Fecha
            <input
              required
              type="date"
              name="date"
              defaultValue={new Date().toISOString().slice(0, 10)}
            />
          </label>
          <label>
            Valor
            <input type="number" step="0.01" name="value" placeholder="0" />
          </label>
          <label>
            Unidad
            <input name="unit" defaultValue={m.unit} key={type} />
          </label>
        </div>
        <label>
          Detalles
          <textarea
            name="details"
            placeholder={type === "GOAL" ? "Describe tu meta, plazo y cómo quieres medirla" : "Series, macros, sensaciones..."}
          />
        </label>
        <button className="primary" disabled={busy}>
          {busy ? "Guardando..." : "Guardar registro"}
        </button>
      </form>
    </div>
  );
}

function Auth({ onAuth }: { onAuth: (u: User, t: string) => void }) {
  const [mode, setMode] = useState<"login" | "register" | "recover">("login");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
  const [recoveryEmail, setRecoveryEmail] = useState("");
  const [recoverySent, setRecoverySent] = useState(false);
  const submitLock=useRef(false);
  return (
    <main className="auth">
      <section className="auth-art">
        <div className="brand light">
          <img src="/anura-mascot.png" alt="" /> ANURA
        </div>
        <div>
          <p>ENTRENA · NÚTRETE · EVOLUCIONA</p>
          <h1>
            Tu cuerpo.
            <br />
            Tu ritmo.
            <br />
            <em>Tu historia.</em>
          </h1>
        </div>
        <small>Movimiento con intención.</small>
      </section>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          if(submitLock.current)return;
          submitLock.current=true;
          const form = e.currentTarget;
          setBusy(true);
          setError("");
          const f = new FormData(form);
          try {
            if (mode === "recover") {
              await api.resetPassword({
                email: String(f.get("email")),
                code: String(f.get("code")),
                newPassword: String(f.get("password")),
              });
              setSuccess("Contraseña actualizada. Ya puedes iniciar sesión.");
              setMode("login");
              form.reset();
              return;
            }
            const r = await api.auth(mode, {
              email: f.get("email"),
              password: f.get("password"),
              displayName: f.get("name"),
            });
            onAuth(r.user, r.token);
          } catch (cause) {
            setError(cause instanceof Error?cause.message:"Revisa los datos e inténtalo de nuevo");
          } finally {
            submitLock.current=false;
            setBusy(false);
          }
        }}
      >
        <div className="auth-install">
          <InstallButton />
        </div>
        <p>BIENVENIDO A ANURA</p>
        <h2>{mode === "login" ? "Continúa tu camino" : mode === "register" ? "Empieza hoy" : "Recupera tu acceso"}</h2>
        {mode === "register" && (
          <label>
            Nombre
            <input required name="name" autoComplete="name" />
          </label>
        )}
        <label>
          Email
          <input required name="email" type="email" autoComplete="email" onChange={(event) => setRecoveryEmail(event.target.value)} />
        </label>
        <label>
          {mode === "recover" ? "Nueva contraseña" : "Contraseña"}
          <input
            required
            minLength={8}
            name="password"
            type="password"
            autoComplete={
              mode === "login" ? "current-password" : "new-password"
            }
          />
        </label>
        {mode === "recover" && (
          <>
            <div className="recovery-help"><span>Usa tu código personal guardado o solicita uno temporal por email.</span><button type="button" disabled={busy || !recoveryEmail} onClick={async()=>{setBusy(true);setError("");try{await api.requestPasswordRecovery(recoveryEmail);setRecoverySent(true);setSuccess("Si la cuenta existe, recibirás un código válido durante 15 minutos.")}catch(cause){setError(cause instanceof Error?cause.message:"No se pudo enviar el correo")}finally{setBusy(false)}}}>{recoverySent?"Reenviar código":"Enviar código por email"}</button></div>
            <label>
              Código de recuperación
              <input required name="code" autoComplete="one-time-code" placeholder="Código personal o del email" />
            </label>
          </>
        )}
        {error && <div className="error">{error}</div>}
        {success && <div className="success">{success}</div>}
        <button className="primary" disabled={busy}>
          {busy ? "Procesando..." : mode === "login" ? "Entrar" : mode === "register" ? "Crear cuenta" : "Cambiar contraseña"}
        </button>
        {mode === "login" && (
          <button type="button" className="text-btn compact" onClick={() => { setMode("recover"); setError(""); }}>
            ¿No recuerdas tu contraseña?
          </button>
        )}
        <button
          type="button"
          className="text-btn"
          onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(""); }}
        >
          {mode === "login" ? "¿Primera vez? Crear cuenta" : "Volver a iniciar sesión"}
        </button>
      </form>
    </main>
  );
}

function AccountModal({ user, onClose,onAvatar,onPreferences }: { user: User; onClose: () => void;onAvatar:(avatar:string)=>void;onPreferences:(preferences:ProfilePreferences)=>void }) {
  const [result, setResult] = useState<{ code: string; expiresAt: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [tab,setProfileTab]=useState<"profile"|"initial"|"work"|"password"|"reminders">("profile"),[message,setMessage]=useState("");
  const [preferences,setPreferences]=useState<ProfilePreferences>({});
  useEffect(()=>{void api.profilePreferences().then(setPreferences)},[]);
  return (
    <div className="overlay">
      <section className="modal account-modal">
        <div className="modal-head">
          <div><small>MI CUENTA</small><h2>Perfil y preferencias</h2></div>
          <button type="button" onClick={onClose} aria-label="Cerrar"><X /></button>
        </div>
        <div className="profile-tabs"><button className={tab==="profile"?"active":""} onClick={()=>setProfileTab("profile")}>Perfil</button><button className={tab==="initial"?"active":""} onClick={()=>setProfileTab("initial")}>Datos iniciales</button><button className={tab==="work"?"active":""} onClick={()=>setProfileTab("work")}>Trabajo y turnos</button><button className={tab==="password"?"active":""} onClick={()=>setProfileTab("password")}>Contraseña</button><button className={tab==="reminders"?"active":""} onClick={()=>setProfileTab("reminders")}>Recordatorios</button></div>
        {message&&<div className="progress-saved">{message}</div>}
        {tab==="work"&&<WorkRoutinePanel/>}
        {tab==="profile"&&<>
        <div className="profile-identity"><label className="profile-avatar-editor" title="Cambiar fotografía">{preferences.avatar_url?<img src={preferences.avatar_url} alt={user.displayName}/>:<img className="mascot-fallback" src="/anura-mascot.png" alt=""/>}<span><Plus/>Cambiar foto</span><input type="file" accept="image/jpeg,image/png,image/webp" onChange={async e=>{const file=e.target.files?.[0];if(!file)return;setBusy(true);try{const avatar=await compressAvatar(file);await api.saveAvatar(avatar);setPreferences(current=>({...current,avatar_url:avatar}));onAvatar(avatar);setMessage("Foto de perfil actualizada")}finally{setBusy(false)}}}/></label><div><small>MI PERFIL</small><h3>{user.displayName}</h3><p>{user.email}</p><em>{goalLabel(preferences.primary_goal)}</em></div></div>
        <form className="profile-sex-setting" onSubmit={async e=>{e.preventDefault();const sex=String(new FormData(e.currentTarget).get("sex")||"UNSPECIFIED");await api.saveProfilePreferences({primaryGoal:preferences.primary_goal||"BODY_RECOMPOSITION",experienceLevel:preferences.experience_level||"BEGINNER",activityLevel:preferences.activity_level||"MODERATE",heightCm:preferences.height_cm,trainingDays:preferences.training_days,limitations:preferences.limitations,biologicalSex:sex,reminderEmailEnabled:preferences.reminder_email_enabled!==false});const next=await api.profilePreferences();setPreferences(next);onPreferences(next);setMessage(sex==="FEMALE"?"Sexo actualizado. La pestaña Ciclo ya está disponible abajo":"Sexo actualizado")}}>
          <label>Sexo para personalizar el seguimiento<select name="sex" value={preferences.biological_sex||"UNSPECIFIED"} onChange={e=>setPreferences(current=>({...current,biological_sex:e.target.value}))}><option value="UNSPECIFIED">Prefiero no indicarlo</option><option value="FEMALE">Mujer</option><option value="MALE">Hombre</option></select></label>
          <button className="primary" type="submit">Guardar</button>
        </form>
        <div className="account-profile"><button type="button" onClick={()=>void navigator.clipboard.writeText(user.email)}><Copy/> Copiar identificador CSV</button></div>
        <p>En los CSV individuales usa siempre <b>{user.email}</b> como <code>user_identifier</code>.</p>
        <p>Guarda este código fuera de ANURA. Podrás usarlo si olvidas tu contraseña; al generar otro, el anterior deja de funcionar.</p>
        <button className="primary" disabled={busy} onClick={async () => {
          setBusy(true);
          try { setResult(await api.createRecoveryCode()); } finally { setBusy(false); }
        }}>{busy ? "Generando…" : result ? "Generar un código nuevo" : "Generar código de recuperación"}</button>
        {result && <div className="invite-code"><small>CÓDIGO PERSONAL</small><strong>{result.code}</strong><button type="button" onClick={() => void navigator.clipboard.writeText(result.code)}><Copy /> Copiar código</button><p>Válido hasta {new Date(result.expiresAt).toLocaleDateString("es")} y para un solo cambio de contraseña.</p></div>}
        </>}
        {tab==="initial"&&<form key={`${preferences.primary_goal}-${preferences.experience_level}-${preferences.activity_level}-${preferences.biological_sex}`} className="profile-form" onSubmit={async e=>{e.preventDefault();const f=new FormData(e.currentTarget),optionalNumber=(name:string)=>f.get(name)?Number(f.get(name)):undefined;await api.saveProfilePreferences({primaryGoal:String(f.get("goal")),experienceLevel:String(f.get("experience")),activityLevel:String(f.get("activity")),heightCm:optionalNumber("height"),trainingDays:optionalNumber("days"),limitations:String(f.get("limitations")||""),biologicalSex:String(f.get("sex")||"UNSPECIFIED"),reminderEmailEnabled:preferences.reminder_email_enabled!==false});setPreferences(await api.profilePreferences());setMessage("Datos iniciales actualizados")}}><p>Actualiza el formulario base que ANURA usa para contextualizar tus planes e informes.</p><label>Sexo<select name="sex" defaultValue={preferences.biological_sex||"UNSPECIFIED"}><option value="UNSPECIFIED">Prefiero no indicarlo</option><option value="FEMALE">Mujer</option><option value="MALE">Hombre</option></select></label><label>Objetivo principal<select name="goal" defaultValue={preferences.primary_goal||"BODY_RECOMPOSITION"}><option value="LOSE_FAT">Bajar grasa</option><option value="GAIN_MUSCLE">Ganar músculo</option><option value="BODY_RECOMPOSITION">Bajar grasa y ganar músculo</option><option value="HEALTH">Mejorar salud y hábitos</option></select></label><label>Experiencia<select name="experience" defaultValue={preferences.experience_level||"BEGINNER"}><option value="BEGINNER">Principiante</option><option value="INTERMEDIATE">Intermedia</option><option value="ADVANCED">Avanzada</option></select></label><label>Actividad diaria<select name="activity" defaultValue={preferences.activity_level||"MODERATE"}><option value="LOW">Baja</option><option value="MODERATE">Moderada</option><option value="HIGH">Alta</option></select></label><div className="profile-form-row"><label>Altura (cm)<input name="height" type="number" min="100" max="250" step="0.1" defaultValue={preferences.height_cm}/></label><label>Días de entreno/semana<input name="days" type="number" min="0" max="7" defaultValue={preferences.training_days}/></label></div><label>Lesiones, limitaciones o contexto<textarea name="limitations" defaultValue={preferences.limitations}/></label><button className="primary">Guardar datos iniciales</button></form>}
        {tab==="password"&&<form className="profile-form" onSubmit={async e=>{e.preventDefault();const f=new FormData(e.currentTarget);await api.changePassword({currentPassword:String(f.get("current")),newPassword:String(f.get("next"))});e.currentTarget.reset();setMessage("Contraseña cambiada correctamente")}}><label>Contraseña actual<input required name="current" type="password" autoComplete="current-password"/></label><label>Nueva contraseña<input required minLength={8} name="next" type="password" autoComplete="new-password"/></label><button className="primary">Cambiar contraseña</button></form>}
        {tab==="reminders"&&<div className="reminder-settings"><div className="reminder-card"><span><b>Resumen mensual por email</b><small>El primer resumen se enviará un mes después de activarlo.</small></span><input type="checkbox" checked={preferences.reminder_email_enabled!==false} onChange={async e=>{const enabled=e.target.checked;await api.saveProfilePreferences({primaryGoal:preferences.primary_goal||"BODY_RECOMPOSITION",experienceLevel:preferences.experience_level||"BEGINNER",activityLevel:preferences.activity_level||"MODERATE",heightCm:preferences.height_cm,trainingDays:preferences.training_days,limitations:preferences.limitations,biologicalSex:preferences.biological_sex,reminderEmailEnabled:enabled});setPreferences({...preferences,reminder_email_enabled:enabled});setMessage("Preferencia guardada")}}/></div>{preferences.last_summary_sent_at&&<p>Último envío: {new Date(preferences.last_summary_sent_at).toLocaleString("es")}</p>}<button className="primary secondary" disabled={busy} onClick={async()=>{setBusy(true);try{await api.testReminder();setMessage("Email de prueba enviado");setPreferences(await api.profilePreferences())}finally{setBusy(false)}}}>{busy?"Enviando…":"Enviar resumen de prueba ahora"}</button></div>}
      </section>
    </div>
  );
}

function compressAvatar(file:File):Promise<string>{return new Promise((resolve,reject)=>{const image=new Image(),url=URL.createObjectURL(file);image.onload=()=>{const size=Math.min(image.width,image.height),sx=(image.width-size)/2,sy=(image.height-size)/2,canvas=document.createElement("canvas");canvas.width=512;canvas.height=512;canvas.getContext("2d")?.drawImage(image,sx,sy,size,size,0,0,512,512);URL.revokeObjectURL(url);resolve(canvas.toDataURL("image/jpeg",.82))};image.onerror=()=>{URL.revokeObjectURL(url);reject(new Error("No se pudo leer la fotografía"))};image.src=url})}

type InstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
};
function InstallButton() {
  const [prompt, setPrompt] = useState<InstallPromptEvent | null>(null);
  const mobile = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
  const standalone =
    window.matchMedia("(display-mode: standalone)").matches ||
    ("standalone" in navigator &&
      (navigator as Navigator & { standalone?: boolean }).standalone);
  useEffect(() => {
    const ready = (event: Event) => {
      event.preventDefault();
      setPrompt(event as InstallPromptEvent);
    };
    window.addEventListener("beforeinstallprompt", ready);
    return () => window.removeEventListener("beforeinstallprompt", ready);
  }, []);
  if (!mobile || standalone) return null;
  return (
    <button
      className="install-btn"
      onClick={async () => {
        if (prompt) {
          await prompt.prompt();
          await prompt.userChoice;
          setPrompt(null);
        } else
          alert(
            "En iPhone: pulsa Compartir y después “Añadir a pantalla de inicio”.",
          );
      }}
    >
      <Download size={16} />
      <span>Instalar</span>
    </button>
  );
}
