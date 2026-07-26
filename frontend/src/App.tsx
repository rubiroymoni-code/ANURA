import { useEffect, useState } from "react";
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
  KeyRound,
  LogOut,
  Plus,
  Scale,
  Target,
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
  TodayWorkout,
  workoutApi,
  User,
} from "./api";
import { NutritionHub } from "./NutritionHub";
import { BodyProgress } from "./BodyProgress";
import { WorkoutHub } from "./WorkoutHub";
import { clearWorkoutOffline } from "./workoutOffline";

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

export function App() {
  const [user, setUser] = useState<User | null>(() => {
    try {
      return JSON.parse(localStorage.getItem("anura-user") || "null");
    } catch {
      return null;
    }
  });
  const [entries, setEntries] = useState<Entry[]>([]);
  const [tab, setTab] = useState<"HOME" | EntryType>("HOME");
  const [modal, setModal] = useState(false);
  const [loading, setLoading] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [nutritionOpen, setNutritionOpen] = useState(false);
  const [workoutOpen, setWorkoutOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [todayMeals, setTodayMeals] = useState<TodayMeal[]>([]);
  const [todayWorkout, setTodayWorkout] = useState<TodayWorkout | null>(null);
  const [todayWorkoutDone, setTodayWorkoutDone] = useState(false);
  const [dailyLoading, setDailyLoading] = useState(false);
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
  return (
    <main className="shell">
      <header>
        <div className="brand">
          <span>A</span> ANURA
        </div>
        <div className="header-actions">
          <button className="icon-btn" onClick={() => setAccountOpen(true)} aria-label="Seguridad de la cuenta">
            <KeyRound size={19} />
          </button>
          <button className="icon-btn" onClick={logout} aria-label="Cerrar sesión">
            <LogOut size={19} />
          </button>
        </div>
      </header>
      <section className="content">
        {tab === "HOME" ? (
          <>
            <div className="hello">
              <p>HOLA, {user.displayName.toUpperCase()}</p>
              <h1>
                Hoy cuenta.
                <br />
                <em>Muévete.</em>
              </h1>
              <span>
                {new Intl.DateTimeFormat("es", {
                  weekday: "long",
                  day: "numeric",
                  month: "long",
                }).format(new Date())}
              </span>
            </div>
            <div className="daily-plan-head"><span>PLAN DE HOY</span><button onClick={() => setNutritionOpen(true)}>Nutrición compartida</button></div>
            <div className="daily-plan-grid">
              <button className="daily-focus workout" onClick={() => setWorkoutOpen(true)}>
                <span className="daily-focus-icon"><Dumbbell /></span>
                <span><small>ENTRENAMIENTO</small><strong>{todayWorkout?.sessionName || "Sesión libre"}</strong><b>{todayWorkout ? `${todayWorkout.exerciseCount} ejercicios · ~${todayWorkout.estimatedMinutes || 45} min` : "No hay plan asignado hoy"}</b></span>
                <em>{todayWorkoutDone || todayItems.some((e) => e.type === "WORKOUT") ? "Hecho" : "Pendiente"}</em>
              </button>
              <div className="daily-focus nutrition">
                <button className="daily-focus-main" onClick={() => setNutritionOpen(true)}>
                  <span className="daily-focus-icon"><Apple /></span>
                  <span><small>COMIDAS</small><strong>{todayMeals.length ? `${todayMeals.filter(m => !m.completed).length} pendientes` : "Sin plan para hoy"}</strong><b>{todayMeals.length ? `${todayMeals.reduce((sum,m) => sum + Number(m.calories || 0),0).toFixed(0)} kcal planificadas` : "Añade una comida o abre tus planes"}</b></span>
                  <em>{todayMeals.length && todayMeals.every(m => m.completed) ? "Hecho" : "Ver plan"}</em>
                </button>
                {todayMeals.length > 0 && <div className="today-meals-mini">{todayMeals.map(meal => <div key={meal.planned_meal_id} className={meal.completed ? "completed" : ""}><button onClick={() => setNutritionOpen(true)}><span><b>{meal.meal_name}</b><small>{meal.recipe} · {Number(meal.calories || 0).toFixed(0)} kcal</small></span></button><button className="meal-complete" disabled={meal.completed || dailyLoading} onClick={async () => {setDailyLoading(true);try{await nutritionApi.completeToday(meal.planned_meal_id);setTodayMeals(rows => rows.map(row => row.planned_meal_id === meal.planned_meal_id ? {...row,completed:true} : row));load();}finally{setDailyLoading(false)}}}>{meal.completed ? "✓" : "Completar"}</button></div>)}</div>}
              </div>
            </div>
            <div className="score">
              <div>
                <small>RITMO DE HOY</small>
                <strong>{Math.min(100, todayItems.length * 25)}%</strong>
              </div>
              <div
                className="ring"
                style={
                  {
                    "--score": `${Math.min(100, todayItems.length * 25) * 3.6}deg`,
                  } as CSSProperties
                }
              >
                <span>{todayItems.length}</span>
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
            <p>{meta[tab].label}</p>
            <h1>{meta[tab].label}s</h1>
          </div>
        )}
        {tab === "WEIGHT" && <BodyProgress />}
        {tab === "WORKOUT" && (
          <button className="workout-launch" onClick={() => setWorkoutOpen(true)}>
            <span><small>ENTRENAMIENTO DE HOY</small><strong>Entrenar ahora</strong><b>Plan, series, descanso y progreso</b></span>
            <Dumbbell />
          </button>
        )}
        {tab === "WEIGHT" && entries.some((entry) => entry.type === "WEIGHT") && <h2 className="subsection-title">Registros anteriores</h2>}
        {(tab !== "WEIGHT" || visible.length > 0) && (
          <EntryList
            entries={visible.slice(0, 12)}
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
              <strong>Nutrición compartida</strong>
              <small>Household, planes, recetas y compra</small>
            </span>
            <b>→</b>
          </button>
        )}
      </section>
      <button className="fab" onClick={() => setModal(true)}>
        <Plus />
      </button>
      <nav>
        {[
          { id: "HOME" as const, icon: Home, label: "Inicio" },
          { id: "WORKOUT" as const, icon: Dumbbell, label: "Entreno" },
          { id: "MEAL" as const, icon: Apple, label: "Nutrición" },
          { id: "WEIGHT" as const, icon: Activity, label: "Evolución" },
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
          busy={loading}
          onClose={() => setModal(false)}
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
        <NutritionHub onClose={() => setNutritionOpen(false)} />
      )}
      {workoutOpen && (
        <WorkoutHub
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
      {accountOpen && <AccountModal onClose={() => setAccountOpen(false)} />}
    </main>
  );
}

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
                    await trainingApi.confirm(preview.importJobId);
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
}: {
  entries: Entry[];
  onDelete: (id: string) => void;
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
            </div>
          </article>
        );
      })}
    </div>
  );
}

function EntryModal({
  onClose,
  onSave,
  busy,
}: {
  onClose: () => void;
  onSave: (e: Omit<Entry, "id">) => void;
  busy: boolean;
}) {
  const [type, setType] = useState<EntryType>("WORKOUT");
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
          {(Object.keys(meta) as EntryType[]).map((t) => {
            const I = meta[t].icon;
            return (
              <button
                type="button"
                className={type === t ? "selected" : ""}
                onClick={() => setType(t)}
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
            placeholder="Series, macros, sensaciones..."
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
  return (
    <main className="auth">
      <section className="auth-art">
        <div className="brand light">
          <span>A</span> ANURA
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
          } catch {
            setError("Revisa los datos e inténtalo de nuevo");
          } finally {
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
          <input required name="email" type="email" autoComplete="email" />
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
          <label>
            Código de recuperación
            <input required name="code" autoComplete="one-time-code" placeholder="Tu código personal" />
          </label>
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

function AccountModal({ onClose }: { onClose: () => void }) {
  const [result, setResult] = useState<{ code: string; expiresAt: string } | null>(null);
  const [busy, setBusy] = useState(false);
  return (
    <div className="overlay">
      <section className="modal account-modal">
        <div className="modal-head">
          <div><small>SEGURIDAD</small><h2>Recuperación de cuenta</h2></div>
          <button type="button" onClick={onClose} aria-label="Cerrar"><X /></button>
        </div>
        <p>Guarda este código fuera de ANURA. Podrás usarlo si olvidas tu contraseña; al generar otro, el anterior deja de funcionar.</p>
        <button className="primary" disabled={busy} onClick={async () => {
          setBusy(true);
          try { setResult(await api.createRecoveryCode()); } finally { setBusy(false); }
        }}>{busy ? "Generando…" : result ? "Generar un código nuevo" : "Generar código de recuperación"}</button>
        {result && <div className="invite-code"><small>CÓDIGO PERSONAL</small><strong>{result.code}</strong><button type="button" onClick={() => void navigator.clipboard.writeText(result.code)}><Copy /> Copiar código</button><p>Válido hasta {new Date(result.expiresAt).toLocaleDateString("es")} y para un solo cambio de contraseña.</p></div>}
      </section>
    </div>
  );
}

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
