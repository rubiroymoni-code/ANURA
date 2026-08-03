import { useEffect, useState } from "react";
import type { CSSProperties } from "react";
import {
  API_BASE,
  Household,
  householdApi,
  nutritionApi,
  NutritionImportPreview,
  NutritionDashboard,
  TodayMeal,
} from "./api";
import {
  ChevronDown,
  CalendarDays,
  ChefHat,
  Download,
  FileUp,
  Home,
  ShoppingBasket,
  Copy,
  MessageCircle,
  Pill,
  SlidersHorizontal,
  Users,
  Utensils,
  X,
} from "lucide-react";
import { HouseholdView } from "./HouseholdView";
import { SupplementsPanel } from "./SupplementsPanel";
import { NutritionPreferencesPanel } from "./NutritionPreferencesPanel";
export function NutritionHub({ onClose,onRegisterMeal }: { onClose: () => void;onRegisterMeal?:(meal:TodayMeal)=>void }) {
  const [section, setSection] = useState<
    "home" | "cook" | "preferences" | "household" | "import" | "shopping" | "supplements" | "plan" | "recipe"
  >("home");
  const [selectedPlan, setSelectedPlan] = useState<string | null>(null);
  const [selectedRecipe, setSelectedRecipe] = useState<string | null>(null);
  const [selectedMeal,setSelectedMeal]=useState<string|null>(null);
  const [households, setHouseholds] = useState<Household[]>([]);
  const [recipes, setRecipes] = useState<Array<{ id: string; name: string }>>(
    [],
  );
  const [plans, setPlans] = useState<
    Array<{ id: string; name: string; version: number; status: string; valid_from?:string;valid_until?:string }>
  >([]);
  const [loadError, setLoadError] = useState("");
  const [todayMeals,setTodayMeals]=useState<TodayMeal[]>([]);
  const [dashboard,setDashboard]=useState<NutritionDashboard|null>(null);
  const [balanceExpanded,setBalanceExpanded]=useState(false);
  const activePlan = plans.find((plan) => plan.status === "ACTIVE") || plans[0];
  const expiry=nutritionPlanExpiry(activePlan?.valid_until);
  useEffect(() => {
    void nutritionApi.today().then(rows=>setTodayMeals(rows.map(localizeMeal))).catch(()=>setTodayMeals([]));
    void nutritionApi.dashboard().then(setDashboard).catch(()=>setDashboard(null));
    void Promise.allSettled([
      householdApi.list(), nutritionApi.recipes(), nutritionApi.plans(),
    ]).then(([householdResult, recipeResult, planResult]) => {
      if (householdResult.status === "fulfilled") setHouseholds(householdResult.value);
      if (recipeResult.status === "fulfilled") setRecipes(recipeResult.value);
      if (planResult.status === "fulfilled") setPlans(planResult.value);
      const rejected = [householdResult, recipeResult, planResult].find(result => result.status === "rejected");
      if (rejected?.status === "rejected") setLoadError(rejected.reason instanceof Error ? rejected.reason.message : "No se pudo cargar nutrición");
    });
  }, []);
  useEffect(()=>{if(todayMeals.some(meal=>/^[A-Z_]+$/.test(meal.meal_type)))setTodayMeals(rows=>rows.map(localizeMeal))},[todayMeals]);
  return (
    <div className="overlay">
      <section className="modal nutrition-hub">
        <div className="modal-head">
          <div>
            <small>{households.length?"NUTRICIÓN Y HOGAR":"MI NUTRICIÓN"}</small>
            <h2>
              {section === "home"
                ? "Hoy"
                : section === "cook"
                  ? "Cocinar hoy"
                  : section === "preferences"
                    ? "Preferencias"
                : section === "household"
                  ? "Mi unidad doméstica"
                  : section === "import"
                    ? "Importar dieta"
                    : section === "shopping"
                      ? "Lista de compra"
                      : section === "supplements"
                        ? "Suplementos"
                      : section === "plan"
                        ? "Plan nutricional"
                        : "Receta"}
            </h2>
          </div>
          <button onClick={onClose}>
            <X />
          </button>
        </div>
        <nav className="nutrition-primary-nav"><button className={section==="home"?"active":""} onClick={()=>setSection("home")}><Utensils/>Hoy</button><button className={section==="plan"?"active":""} disabled={!activePlan} onClick={()=>{if(activePlan){setSelectedPlan(activePlan.id);setSection("plan")}}}><CalendarDays/>Plan</button><button className={section==="cook"||section==="recipe"?"active":""} onClick={()=>setSection("cook")}><ChefHat/>Cocina</button><button className={section==="shopping"?"active":""} onClick={()=>setSection("shopping")}><ShoppingBasket/>Compra</button><button className={section==="supplements"?"active":""} onClick={()=>setSection("supplements")}><Pill/>Suplementos</button></nav>
        <div className="nutrition-hub-content" onClickCapture={(event)=>{const target=(event.target as HTMLElement).closest(".nutrition-today .today-recipe-link");if(!target||!onRegisterMeal)return;event.preventDefault();event.stopPropagation();const buttons=Array.from(event.currentTarget.querySelectorAll(".nutrition-today .today-recipe-link"));const meal=todayMeals[buttons.indexOf(target as HTMLButtonElement)];if(meal)onRegisterMeal(meal)}}>
        {loadError && <div className="error" role="alert">{loadError}</div>}
        {expiry.urgent&&<section className="nutrition-expiry-warning" role="alert"><CalendarDays/><span><b>{expiry.expired?"Plan caducado":"Actualiza tu plan"}</b><small>{expiry.message} Genera el prompt con tu progreso e importa la siguiente versión para mantener comidas, macros y compra al día.</small></span></section>}
        {section === "home" && (
          <>
            {dashboard&&<section className={`nutrition-overview ${balanceExpanded?"expanded":""}`}><button className="nutrition-overview-summary" onClick={()=>setBalanceExpanded(value=>!value)}><span><small>BALANCE DE HOY</small><b>{Number(dashboard.consumed.calories||0).toFixed(0)} / {Number(dashboard.target.calories||dashboard.planned.calories||0).toFixed(0)} kcal</b><em>{activePlan?`${activePlan.name} · versión ${activePlan.version}`:"Sin plan activo"}</em></span><strong>{Math.round(Number(dashboard.target.calories||dashboard.planned.calories||0)?Number(dashboard.consumed.calories||0)/Number(dashboard.target.calories||dashboard.planned.calories||1)*100:0)}%</strong><ChevronDown/></button>{balanceExpanded&&<div className="nutrition-overview-detail"><NutritionBalance data={dashboard}/>{activePlan&&<button className="primary" onClick={()=>{setSelectedPlan(activePlan.id);setSection("plan")}}>Ver plan actual</button>}</div>}</section>}
            <section className="nutrition-today"><div><small>HOY</small><h3>Lo que te toca comer</h3><p>Abre una comida para consultar su receta. Pulsa el check otra vez si necesitas deshacerla.</p></div>{todayMeals.length?todayMeals.map(meal=><article className={meal.status!=="PENDING"?"completed":""} key={meal.planned_meal_id}><button className="today-recipe-link" onClick={()=>{const recipe=recipes.find(r=>r.name.trim().toLowerCase()===meal.recipe.trim().toLowerCase());if(recipe){setSelectedRecipe(recipe.id);setSelectedMeal(meal.planned_meal_id);setSection("recipe")}}}><small>{meal.meal_type}</small><b>{meal.recipe||meal.meal_name}</b><em>{Number(meal.calories||0).toFixed(0)} kcal · P {Number(meal.protein||0).toFixed(0)} · C {Number(meal.carbohydrates||0).toFixed(0)} · G {Number(meal.fat||0).toFixed(0)}</em></button><button aria-label={meal.status==="PENDING"?`Completar ${meal.meal_name}`:`Deshacer ${meal.meal_name}`} title={meal.status==="PENDING"?"Marcar como hecha":"Deshacer registro"} onClick={async()=>{if(meal.status==="PENDING")await nutritionApi.completeToday(meal.planned_meal_id);else await nutritionApi.undoToday(meal.planned_meal_id);setTodayMeals((await nutritionApi.today()).map(localizeMeal));setDashboard(await nutritionApi.dashboard())}}>{meal.status!=="PENDING"?"✓":"○"}</button></article>):<p>No hay comidas asignadas para hoy en el plan activo.</p>}</section>
            <details className="nutrition-tools"><summary>Gestión y configuración</summary><div className="nutrition-menu">
              <button onClick={() => setSection("household")}>
                <Users />
                <b>Mi unidad doméstica</b>
                <span>
                  {households[0]?.name || "Crear o aceptar invitación"}
                </span>
              </button>
              <button onClick={() => setSection("import")}>
                <FileUp />
                <b>Importar dieta</b>
                <span>Individual, compartida o recetas</span>
              </button>
              <button onClick={() => setSection("preferences")}>
                <SlidersHorizontal />
                <b>Preferencias alimentarias</b>
                <span>Gustos, exclusiones y planificación</span>
              </button>
            </div></details>
            <details className="nutrition-tools nutrition-history"><summary>Planes anteriores y versiones</summary>
            {plans.map((p) => (
              <button
                className="nutrition-row"
                key={p.name + p.version}
                onClick={() => {
                  setSelectedPlan(p.id);
                  setSection("plan");
                }}
              >
                <Utensils />
                <span>
                  <b>{p.name}</b>
                  <small>
                    Versión {p.version} · {p.status}
                  </small>
                </span>
              </button>
            ))}
            </details>
          </>
        )}
        {section === "household" && (
          <HouseholdView
            households={households}
            refresh={() => householdApi.list().then(setHouseholds)}
          />
        )}{" "}
        {section === "import" && <NutritionImport />}
        {section === "shopping" && <Shopping plans={plans} />}
        {section === "preferences" && <NutritionPreferencesPanel />}
        {section === "cook" && <CookToday meals={todayMeals} recipes={recipes} open={(recipeId,mealId)=>{setSelectedRecipe(recipeId);setSelectedMeal(mealId);setSection("recipe")}} />}
        {section === "supplements" && <SupplementsPanel />}
        {section === "plan" && selectedPlan && <PlanView id={selectedPlan} status={plans.find(plan=>plan.id===selectedPlan)?.status||""} onDeleted={async()=>{setPlans(await nutritionApi.plans());setSelectedPlan(null);setSection("home")}} />}
        {section === "recipe" && selectedRecipe && (
          <RecipeView id={selectedRecipe} mealId={selectedMeal} />
        )}
        {section !== "home" && (
          <button className="text-btn" onClick={() => setSection("home")}>
            ← Volver a nutrición
          </button>
        )}
        </div>
      </section>
    </div>
  );
}

function nutritionPlanExpiry(value?:string){if(!value)return{urgent:false,expired:false,message:""};const days=Math.ceil((new Date(`${value}T23:59:59`).getTime()-Date.now())/86400000);return{urgent:days<=1,expired:days<0,message:days<0?"El plan ya ha terminado.":days===0?"El plan termina hoy.":"Al plan le queda un día."}}
function NutritionBalance({data}:{data:NutritionDashboard}){
  const [editing,setEditing]=useState(false);
  const [current,setCurrent]=useState(data);
  const consumed=Number(current.consumed.calories||0),recommended=Number(current.target.calories||current.planned.calories||0),percent=recommended?Math.min(100,consumed/recommended*100):0;
  const weekConsumed=current.week.reduce((sum,row)=>sum+Number(row.calories||0),0),elapsed=Math.max(1,new Date().getDay()||7),weekTarget=recommended*elapsed;
  const macro=(key:"protein"|"carbohydrates"|"fat",label:string)=><span><b>{Number(current.consumed[key]||0).toFixed(0)}<small> / {Number(current.target[key]||current.planned[key]||0).toFixed(0)} g</small></b><em>{label}</em></span>;
  return <section className="nutrition-balance"><div className="balance-head"><span><small>BALANCE DE HOY</small><h3>{consumed.toFixed(0)} <em>/ {recommended.toFixed(0)} kcal</em></h3><p>{recommended?`${Math.max(0,recommended-consumed).toFixed(0)} kcal disponibles según tu objetivo`:`Configura tu objetivo para comparar el consumo.`}</p></span><div className="nutrition-ring" style={{"--nutrition-progress":`${percent*3.6}deg`} as CSSProperties}><b>{percent.toFixed(0)}%</b></div></div><div className="macro-balance">{macro("protein","Proteína")}{macro("carbohydrates","Carbohidratos")}{macro("fat","Grasas")}</div><div className="week-balance"><span><small>ESTA SEMANA</small><b>{weekConsumed.toFixed(0)} / {weekTarget.toFixed(0)} kcal</b></span><div><i style={{width:`${weekTarget?Math.min(100,weekConsumed/weekTarget*100):0}%`}}/></div></div><button className="target-edit" onClick={()=>setEditing(!editing)}>{editing?"Cancelar":"Ajustar objetivos nutricionales"}</button>{editing&&<form className="nutrition-target-form" onSubmit={async e=>{e.preventDefault();const f=new FormData(e.currentTarget),value=(key:string)=>Number(f.get(key)||0);await nutritionApi.saveTarget({validFrom:new Date().toISOString().slice(0,10),calories:value("calories"),protein:value("protein"),carbohydrates:value("carbohydrates"),fat:value("fat")});setCurrent(await nutritionApi.dashboard());setEditing(false)}}><label>Kcal diarias<input required name="calories" type="number" min="800" defaultValue={current.target.calories}/></label><label>Proteína (g)<input name="protein" type="number" min="0" defaultValue={current.target.protein}/></label><label>Carbos (g)<input name="carbohydrates" type="number" min="0" defaultValue={current.target.carbohydrates}/></label><label>Grasas (g)<input name="fat" type="number" min="0" defaultValue={current.target.fat}/></label><button>Guardar objetivos</button></form>}</section>
}
function LegacyHouseholdView({
  households,
  refresh,
}: {
  households: Household[];
  refresh: () => void;
}) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [generated, setGenerated] = useState("");
  const [message, setMessage] = useState("");
  const h = households[0];
  return (
    <div>
      {h ? (
        <>
          <div className="household-card">
            <Users />
            <div>
              <b>{h.name}</b>
              <small>Tu rol: {h.role}</small>
            </div>
          </div>
          {h.role === "OWNER" && (
            <>
              <label>
                Email de la persona (opcional; no se envía)
                <input
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  type="email"
                />
              </label>
              <button
                className="primary"
                onClick={async () => {
                  const r = await householdApi.invite(h.id, email);
                  setGenerated(r.code);
                  setMessage(
                    r.recipientStatus === "NEW_USER"
                      ? `Ese email todavía no tiene cuenta. Se ha preparado una invitación para registrarse en ANURA${r.deliveryStatus === "SENT" ? " y se ha enviado por correo" : ""}.`
                      : r.recipientStatus === "REGISTERED_USER"
                        ? `Usuario encontrado${r.deliveryStatus === "SENT" ? ": invitación enviada por correo" : ""}.`
                        : "Código listo para compartir.",
                  );
                }}
              >
                {email ? "Crear invitación para este email" : "Generar código para compartir"}
              </button>
              {generated && (
                <div className="invite-code">
                  <small>CÓDIGO TEMPORAL · 24 HORAS</small>
                  <strong>{generated}</strong>
                  <div className="invite-actions">
                    <button type="button" onClick={() => void navigator.clipboard.writeText(generated)}>
                      <Copy /> Copiar
                    </button>
                    <a
                      href={`https://wa.me/?text=${encodeURIComponent(`Únete a mi unidad doméstica en ANURA con este código: ${generated}`)}`}
                      target="_blank"
                      rel="noreferrer"
                    >
                      <MessageCircle /> WhatsApp
                    </a>
                  </div>
                </div>
              )}
              {message && <p className="form-note">{message} ANURA todavía no envía emails.</p>}
            </>
          )}
        </>
      ) : (
        <>
          <label>
            Nombre de la unidad
            <input value={name} onChange={(e) => setName(e.target.value)} />
          </label>
          <button
            className="primary"
            onClick={async () => {
              const household = await householdApi.create(name);
              const invitation = await householdApi.invite(household.household.id);
              setGenerated(invitation.code);
              refresh();
            }}
          >
            Crear unidad doméstica
          </button>
          {generated && (
            <div className="invite-code">
              <small>CÓDIGO DE TU NUEVA UNIDAD · 24 HORAS</small>
              <strong>{generated}</strong>
              <div className="invite-actions">
                <button type="button" onClick={() => void navigator.clipboard.writeText(generated)}>
                  <Copy /> Copiar
                </button>
                <a
                  href={`https://wa.me/?text=${encodeURIComponent(`Únete a mi unidad doméstica en ANURA con este código: ${generated}`)}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  <MessageCircle /> Compartir por WhatsApp
                </a>
              </div>
            </div>
          )}
          <div className="or">o aceptar invitación</div>
          <label>
            Código
            <input value={code} onChange={(e) => setCode(e.target.value)} />
          </label>
          <button
            className="primary secondary"
            onClick={async () => {
              await householdApi.accept(code);
              refresh();
            }}
          >
            Unirme
          </button>
        </>
      )}
    </div>
  );
}
function NutritionImport() {
  const currentUser=JSON.parse(localStorage.getItem("anura-user")||"{}");
  const [csvHouseholds,setCsvHouseholds]=useState<Household[]>([]);
  const [type, setType] = useState<"diet" | "shared-diet" | "recipes">(
    "shared-diet",
  );
  const [file, setFile] = useState<File | null>(null);
  const [p, setP] = useState<NutritionImportPreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState("");
  useEffect(()=>{void householdApi.list().then(setCsvHouseholds)},[]);
  if (done)
    return (
      <div className="import-success">
        <Utensils />
        <h3>Importación completada</h3>
      </div>
    );
  return (
    <div>
      <div className="import-types">
        {(
          [
            ["diet", "Individual"],
            ["shared-diet", "Compartida"],
            ["recipes", "Recetas"],
          ] as const
        ).map((x) => (
          <button
            className={type === x[0] ? "selected" : ""}
            onClick={() => {
              setType(x[0]);
              setP(null);
            }}
          >
            {x[1]}
          </button>
        ))}
      </div>
      {type!=="recipes"&&<div className="csv-identity"><small>DATOS PARA RELLENAR LA PLANTILLA</small>{type==="diet"?<><b>{currentUser.email}</b><span>Usar en <code>user_identifier</code>.</span></>:<><b>{csvHouseholds[0]?.name||"Crea primero una unidad doméstica"}</b><span>Usar como <code>household_identifier</code>. En <code>user_1_identifier</code> y <code>user_2_identifier</code>, usa los emails de los miembros.</span></>}</div>}
      <a
        className="template-link"
        href={`${API_BASE}/nutrition-import-schemas/${type}/template`}
      >
        <Download />
        Descargar plantilla
      </a>
      <label className="file-drop">
        <FileUp />
        <b>{file?.name || "Seleccionar CSV"}</b>
        <input
          type="file"
          accept=".csv"
          onChange={(e) => {
            setFile(e.target.files?.[0] || null);
            setP(null);
          }}
        />
      </label>
      {p && (
        <div className="preview-card">
          <span className={p.confirmable ? "valid" : "invalid"}>
            {p.confirmable ? "Archivo válido" : "Corrige los errores"}
          </span>
          <div className="preview-stats">
            <b>
              {p.recipes}
              <small>recetas</small>
            </b>
            <b>
              {p.ingredients}
              <small>ingredientes</small>
            </b>
            <b>
              {p.users.length}
              <small>personas</small>
            </b>
          </div>
          {p.issues.map((i, n) => (
            <details key={n}>
              <summary>
                Fila {i.row || "—"} · {i.column || "archivo"}
              </summary>
              <p>{i.message}</p>
            </details>
          ))}
        </div>
      )}
      {error && <div className="error">{error}</div>}
      <button
        className="primary"
        disabled={!file || busy || (!!p && !p.confirmable)}
        onClick={async () => {
          setBusy(true);
          setError("");
          try {
            if (!p) setP(await nutritionApi.preview(type, file!));
            else {
              await nutritionApi.confirm(p.importJobId);
              setDone(true);
            }
          } catch (cause) {
            setError(
              cause instanceof Error
                ? cause.message
                : "No se pudo completar la importación",
            );
          } finally {
            setBusy(false);
          }
        }}
      >
        {busy
          ? "Procesando..."
          : p
            ? "Confirmar importación"
            : "Validar y previsualizar"}
      </button>
    </div>
  );
}
function CookToday({meals,recipes,open}:{meals:TodayMeal[];recipes:Array<{id:string;name:string}>;open:(recipeId:string,mealId:string)=>void}){return <section className="cook-today"><div className="food-pref-hero"><ChefHat/><span><small>COCINA SIN CÁLCULOS</small><h3>Preparaciones de hoy</h3><p>Abre una receta para ver lo de cada persona y el total conjunto ingrediente por ingrediente.</p></span></div>{meals.length?meals.map(meal=>{const recipe=recipes.find(item=>item.name.trim().toLocaleLowerCase("es")===meal.recipe.trim().toLocaleLowerCase("es"));return <button key={meal.planned_meal_id} disabled={!recipe} onClick={()=>recipe&&open(recipe.id,meal.planned_meal_id)}><span><small>{meal.meal_type}</small><b>{meal.recipe}</b><em>{Number(meal.calories||0).toFixed(0)} kcal para ti</em></span><strong>Ver reparto →</strong></button>}):<div className="empty">No hay preparaciones asignadas para hoy.</div>}</section>}

function RecipeView({ id,mealId }: { id: string;mealId:string|null }) {
  const [rows, setRows] = useState<Array<Record<string, unknown>>>([]);
  const [portions,setPortions]=useState<Array<Record<string,unknown>>>([]);
  useEffect(() => {
    void nutritionApi.recipe(id).then(setRows);
    if(mealId)void nutritionApi.mealPortions(mealId).then(setPortions);else setPortions([]);
  }, [id,mealId]);
  const calories = rows.reduce(
    (sum, r) =>
      sum + (Number(r.calories_100 || 0) * Number(r.quantity || 0)) / 100,
    0,
  );
  return (
    <div>
      <div className="household-card">
        <Utensils />
        <div>
          <b>{String(rows[0]?.name || "Receta")}</b>
          <small>{portions.length>0?`${portions.reduce((sum,person)=>sum+Number(person.calories||0),0).toFixed(0)} kcal para la unidad`:`${calories.toFixed(0)} kcal totales calculadas`}</small>
        </div>
      </div>
      {portions.length>0&&<section className="recipe-person-portions">
        <div><small>CANTIDADES PARA ESTA COMIDA</small><h3>Qué corresponde a cada persona</h3></div>
        {portions.map(person=><PortionCard key={String(person.user_id)} person={person}/>)}
        <article className="recipe-combined"><header><b>Total para cocinar · crudo/seco</b><span>{portions.reduce((sum,p)=>sum+Number(p.quantity||0),0).toFixed(0)} g</span></header><div className="portion-ingredients combined">{combinedIngredients(portions).map((ingredient,index)=><span key={`${ingredient.name}-${index}`}><b>{ingredient.name}</b><em>{ingredient.quantity.toFixed(0)} {ingredient.unit}</em></span>)}</div></article>
        <p className="cooked-portions-note"><b>Cómo usar el reparto cocinado</b> Cocina el total conjunto indicado arriba, pesa el alimento ya preparado y sirve aproximadamente la cantidad cocinada mostrada para cada persona. Son equivalencias medias: el agua, el método y el punto de cocción pueden variar el peso.</p>
      </section>}
      {portions.length===0&&rows.map((r, n) => (
        <div className="nutrition-row" key={n}>
          <span>
            <b>{String(r.ingredient)}</b>
            <small>
              {String(r.quantity)} {String(r.unit)} ·{" "}
              {Number(r.protein_100 || 0)} g proteína/100
            </small>
          </span>
        </div>
      ))}
    </div>
  );
}

function PlanView({ id,status,onDeleted }: { id: string;status:string;onDeleted:()=>void }) {
  const [rows, setRows] = useState<Array<Record<string, unknown>>>([]);
  const [selectedDay,setSelectedDay]=useState(new Date().getDay()||7);
  const currentUser = JSON.parse(localStorage.getItem("anura-user") || "{}");
  useEffect(() => {
    void nutritionApi.week(id).then(setRows);
  }, [id]);
  const todayDay=new Date().getDay()||7;
  const mine=rows.filter(row=>String(row.user_id)===String(currentUser.id));
  const days=[...new Map(mine.map(row=>[Number(row.day_number),String(row.day_name||`Día ${row.day_number}`)])).entries()].sort((a,b)=>a[0]-b[0]);
  const dayRows=mine.filter(row=>selectedDay===0||Number(row.day_number)===selectedDay).sort((a,b)=>Number(a.day_number)-Number(b.day_number)||Number(a.meal_order||0)-Number(b.meal_order||0));
  return (
    <div className="plan-agenda">
      <div className="plan-day-tabs">{days.map(([day,name])=><button key={day} className={selectedDay===day?"active":""} onClick={()=>setSelectedDay(day)}><b>{day===todayDay?"Hoy":name.slice(0,3)}</b><small>Día {day}</small></button>)}<button className={selectedDay===0?"active":""} onClick={()=>setSelectedDay(0)}><b>Todo</b><small>semana</small></button></div>
      <div className="plan-agenda-intro"><CalendarDays/><span><b>{selectedDay===todayDay?"Tu menú de hoy":selectedDay===0?"Tu semana completa":`Tu menú del día ${selectedDay}`}</b><small>Para cantidades conjuntas y reparto abre Cocina.</small></span></div>
      {!dayRows.length&&<div className="empty">No tienes comidas asignadas para este día.</div>}
      <div className="plan-agenda-list">{dayRows.map((meal,index)=><article key={`${meal.planned_meal_id||meal.meal_name}-${index}`}><span className="agenda-time"><small>{mealTypeLabel(String(meal.meal_type))}</small><b>{String(meal.meal_name)}</b><em>{String(meal.recipe)}</em></span><span className="agenda-macros"><b>{Number(meal.calories||0).toFixed(0)} kcal</b><small>P {Number(meal.protein||0).toFixed(0)} · C {Number(meal.carbohydrates||0).toFixed(0)} · G {Number(meal.fat||0).toFixed(0)}</small></span>{selectedDay===0&&<i>Día {String(meal.day_number)}</i>}</article>)}</div>
      <div className="plan-actions"><span className={status==="ACTIVE"?"active":""}>{status==="ACTIVE"?"● Plan activo":status||"Plan"}</span>{status!=="ACTIVE"&&<button className="primary" onClick={async()=>{await nutritionApi.activate(id);location.reload()}}>Activar este plan</button>}<button className="danger" onClick={async()=>{if(confirm("¿Eliminar este plan nutricional? Se borrarán sus días y comidas planificadas. Esta acción no se puede deshacer.")){await nutritionApi.deletePlan(id);onDeleted()}}}>Eliminar plan</button></div>
    </div>
  );
}

function ingredientRows(value:unknown):Array<{name:string;quantity:number;unit:string}>{if(Array.isArray(value))return value as Array<{name:string;quantity:number;unit:string}>;if(typeof value==="string")try{const parsed=JSON.parse(value);return Array.isArray(parsed)?parsed:[]}catch{return[]}return[]}
function combinedIngredients(people:Array<Record<string,unknown>>){const totals=new Map<string,{name:string;quantity:number;unit:string}>();people.forEach(person=>ingredientRows(person.ingredients).forEach(ingredient=>{const key=`${ingredient.name}|${ingredient.unit}`,current=totals.get(key);totals.set(key,{name:ingredient.name,unit:ingredient.unit||"g",quantity:(current?.quantity||0)+Number(ingredient.quantity||0)})}));return [...totals.values()]}

type PortionIngredient={name:string;quantity:number;unit:string};
function PortionCard({person}:{person:Record<string,unknown>}){const ingredients=ingredientRows(person.ingredients),cooked=cookedIngredientRows(ingredients);return <article><header><b>{String(person.display_name)}</b><span>{Number(person.quantity||0).toFixed(0)} g · {Number(person.calories||0).toFixed(0)} kcal</span></header><div className="portion-ingredients">{ingredients.map((ingredient,index)=><span key={`${ingredient.name}-${index}`}><b>{ingredient.name}</b><em>{ingredient.quantity.toFixed(0)} {ingredient.unit}</em></span>)}</div>{cooked.length>0&&<div className="cooked-portion"><small>PARA SERVIR · PESO YA COCINADO</small><div className="portion-ingredients cooked">{cooked.map((ingredient,index)=><span key={`${ingredient.name}-${index}`}><b>{ingredient.name}</b><em>≈ {ingredient.quantity.toFixed(0)} g</em></span>)}</div></div>}</article>}
function cookedIngredientRows(rows:PortionIngredient[]){return rows.flatMap(row=>{const factor=cookingFactor(row.name,row.unit);return factor?[{...row,quantity:Number(row.quantity||0)*factor,unit:"g"}]:[]})}
function cookingFactor(name:string,unit:string){if(!/^g(r|ramo|ramos)?$/i.test((unit||"g").trim()))return null;const value=name.toLocaleLowerCase("es");if(/cocid|cocinad|asado|plancha|hervid|preparad/.test(value))return null;if(/arroz/.test(value))return 2.8;if(/pasta|macarr|espaguet|tallar/.test(value))return 2.4;if(/quinoa/.test(value))return 3;if(/cusc[uú]s|couscous/.test(value))return 2.5;if(/lentej/.test(value))return 2.5;if(/garbanz/.test(value))return 2.4;if(/alubia|jud[ií]a|frijol/.test(value))return 2.5;if(/avena/.test(value))return 3;if(/pollo|pavo/.test(value))return .75;if(/ternera|vacuno|cerdo/.test(value))return .72;if(/salm[oó]n|merluza|at[uú]n|bacalao|pescado/.test(value))return .8;if(/br[oó]coli|coliflor|calabac[ií]n|berenjena|verdura/.test(value))return .85;return null}

function Shopping({ plans }: { plans: Array<{ id: string;name:string;status:string }> }) {
  const weekOptions=calendarWeeks();
  const [lists,setLists]=useState<Awaited<ReturnType<typeof nutritionApi.shopping>>>([]),[items,setItems]=useState<Awaited<ReturnType<typeof nutritionApi.items>>>([]),[week,setWeek]=useState(weekOptions[0].number),[listId,setListId]=useState(""),[error,setError]=useState("");
  const [pantry,setPantry]=useState<Awaited<ReturnType<typeof nutritionApi.pantry>>>([]),[pantryOpen,setPantryOpen]=useState(false);
  const activePlan=plans.find(p=>p.status==="ACTIVE")||plans[0];
  const load=async(preferred?:string)=>{const next=await nutritionApi.shopping();setLists(next);const selected=next.find(x=>x.id===preferred)||next.find(x=>x.week_number===week);setListId(selected?.id||"");setItems(selected?await nutritionApi.items(selected.id):[])};
  const loadPantry=async()=>setPantry(await nutritionApi.pantry());
  useEffect(()=>{void load();void loadPantry()},[]);
  const generate=async()=>{if(!activePlan)return;setError("");try{const result=await nutritionApi.generateShopping(activePlan.id,week);await load(result.id)}catch(cause){if(cause instanceof Error&&cause.message.includes("modificada")&&confirm("La lista tiene cambios manuales. ¿Regenerarla igualmente?")){const result=await nutritionApi.generateShopping(activePlan.id,week,true);await load(result.id)}else setError(cause instanceof Error?cause.message:"No se pudo generar")}};
  const refreshItems=async()=>{if(listId)setItems(await nutritionApi.items(listId))};
  const categories=[...new Set(items.map(i=>i.category||"OTHER"))];
  return <div className="shopping-view">
    <div className="shopping-toolbar"><label>Periodo de compra<select value={week} onChange={async e=>{const selectedWeek=Number(e.target.value);setWeek(selectedWeek);const list=lists.find(x=>x.week_number===selectedWeek);setListId(list?.id||"");setItems(list?await nutritionApi.items(list.id):[]);}}>{weekOptions.map(option=><option key={option.number} value={option.number}>{option.label}</option>)}</select></label><button className="primary" disabled={!activePlan} onClick={generate}>{lists.some(x=>x.week_number===week)?"Actualizar lista":"Generar lista"}</button></div>
    <div className="shopping-management"><button onClick={()=>setPantryOpen(value=>!value)}>{pantryOpen?"Cerrar despensa":`Ver despensa (${pantry.length})`}</button>{listId&&<button onClick={async()=>{if(confirm("¿Vaciar esta lista? Podrás generarla de nuevo desde el plan activo.")){await nutritionApi.resetShopping(listId);await load()}}}>Vaciar lista actual</button>}</div>
    {pantryOpen&&<section className="pantry-manager"><header><div><small>INVENTARIO DOMÉSTICO</small><h3>Saldo previsto en casa</h3></div>{pantry.length>0&&<button className="danger" onClick={async()=>{if(confirm("¿Vaciar toda la despensa doméstica?")){await nutritionApi.clearPantry();await loadPantry()}}}>Vaciar despensa</button>}</header><p className="pantry-explanation"><b>¿Qué significa esta cantidad?</b> Es lo que quedará después de consumir lo reservado para las listas semanales que ya has generado. Al vaciar o regenerar una lista, ANURA devuelve esa reserva y vuelve a calcularla.</p>{pantry.length===0?<p className="empty">La despensa está vacía.</p>:pantry.map(item=><article key={`${item.ingredient_id}-${item.unit}`}><div className="pantry-product"><b>{item.name}</b><small>Quedará tras esta semana</small></div><label><input aria-label={`Saldo previsto de ${item.name}`} type="number" min="0" step="0.001" value={item.quantity} onChange={e=>setPantry(current=>current.map(row=>row===item?{...row,quantity:Number(e.target.value)}:row))}/><span>{item.unit}</span></label><button onClick={async()=>{await nutritionApi.updatePantry(item.ingredient_id,item.quantity,item.unit);await loadPantry()}}>Guardar</button><button className="danger-link" onClick={async()=>{await nutritionApi.deletePantry(item.ingredient_id,item.unit);await loadPantry()}}>Eliminar</button></article>)}<form className="pantry-add" onSubmit={async e=>{e.preventDefault();const f=new FormData(e.currentTarget);await nutritionApi.addPantry({name:String(f.get("name")),category:String(f.get("category")),quantity:Number(f.get("quantity")),unit:String(f.get("unit"))});e.currentTarget.reset();await loadPantry()}}><b>Añadir algo que ya tienes</b><input name="name" required placeholder="Ej. Arroz"/><select name="category"><option value="PANTRY">Despensa</option><option value="FRUIT_VEGETABLES">Fruta y verdura</option><option value="MEAT_FISH">Carnes y pescados</option><option value="DAIRY">Lácteos</option><option value="OTHER">Otros</option></select><input name="quantity" required type="number" min="0" step="0.001" placeholder="Cantidad"/><UnitSelect/><button className="primary">Añadir</button></form></section>}
    {error&&<p className="error">{error}</p>}
    {!listId?<div className="empty">Genera la lista de la semana desde tu plan activo.</div>:<>
      <div className="shopping-export prominent"><button className="primary secondary" onClick={()=>navigator.clipboard.writeText(items.filter(i=>!i.purchased&&i.quantity>0).map(i=>`${i.name}: ${displayQuantity(i.quantity,i.unit)}`).join("\n"))}>Copiar lista</button><button className="primary" onClick={()=>{void navigator.clipboard.writeText(`Lista ANURA · ${weekOptions.find(x=>x.number===week)?.label}\n\n${items.filter(i=>!i.purchased&&i.quantity>0).map(i=>`☐ ${i.name}: ${displayQuantity(i.quantity,i.unit)}`).join("\n")}`);window.open("https://keep.google.com/#home","_blank","noopener,noreferrer")}}>Abrir en Google Keep</button></div>
      <div className="pantry-note"><b>Cómo funciona la despensa:</b> al generar la lista, ANURA reserva lo que usarás esta semana. La despensa muestra el saldo que quedará después; indica lo que compras realmente y marca el check para añadir cualquier sobrante futuro.</div>
      {categories.map(category=><section className="shopping-category" key={category}><h4>{categoryLabel(category)}</h4>{items.filter(i=>(i.category||"OTHER")===category).map(i=>{const covered=i.quantity<=0&&i.pantry_used>=i.required_quantity;return <article className={`shopping-item ${i.purchased?"purchased":""} ${covered?"covered":""}`} key={i.id}>
        {covered?<span className="shopping-check">✓</span>:<button className="shopping-check" onClick={async event=>{const input=event.currentTarget.parentElement?.querySelector<HTMLInputElement>("input");if(input)await nutritionApi.shoppingQuantity(i.id,Number(input.value));await nutritionApi.toggle(i.id);await refreshItems()}}>{i.purchased?"✓":"○"}</button>}<div><b>{i.name}</b><small>{covered?`Cubierto por despensa · ${displayQuantity(i.pantry_used,i.unit)} disponibles`:`Necesario: ${displayQuantity(i.required_quantity,i.unit)}${i.pantry_used>0?` · Ya disponible: ${displayQuantity(i.pantry_used,i.unit)}`:""}`}</small></div>{covered?<strong className="pantry-covered">No comprar</strong>:<label>Comprado<input type="number" min="0" step="0.001" defaultValue={i.quantity}/><span>{i.unit}</span></label>}
      </article>})}</section>)}
      <form className="shopping-add" onSubmit={async e=>{e.preventDefault();const f=new FormData(e.currentTarget);await nutritionApi.addShoppingItem(listId,{name:String(f.get("name")),category:String(f.get("category")),quantity:Number(f.get("quantity")),unit:String(f.get("unit"))});e.currentTarget.reset();await refreshItems()}}><b>Añadir a la casa</b><input name="name" required placeholder="Producto"/><select name="category"><option value="OTHER">Otros</option><option value="PANTRY">Despensa</option><option value="FRUIT_VEGETABLES">Fruta y verdura</option><option value="MEAT_FISH">Carnes y pescados</option><option value="DAIRY">Lácteos</option></select><input name="quantity" required type="number" min="0" step="0.001" placeholder="Cantidad"/><UnitSelect/><button className="primary">Añadir</button></form>
    </>}
  </div>;
}
function categoryLabel(value:string){return ({FRUIT_VEGETABLES:"Fruta y verdura",FRUTA:"Fruta",VERDURA:"Verdura",MEAT_FISH:"Carnes y pescados",PROTEINA:"Proteínas",EGGS:"Huevos",HUEVO:"Huevos",DAIRY:"Lácteos",LACTEO:"Lácteos",CEREALS_LEGUMES:"Cereales y legumbres",CEREAL:"Cereales",LEGUMBRE:"Legumbres",FROZEN:"Congelados",PANTRY:"Despensa",DESPENSA:"Despensa",DRINKS:"Bebidas",BEBIDA:"Bebidas",FRUTO_SECO:"Frutos secos",OTHER:"Otros"} as Record<string,string>)[value]||value}
function UnitSelect(){return <select name="unit" required aria-label="Unidad"><option value="g">Gramos (g)</option><option value="kg">Kilogramos (kg)</option><option value="ml">Mililitros (ml)</option><option value="l">Litros (L)</option><option value="ud">Unidades (ud)</option></select>}
function displayQuantity(value:number,unit:string){let amount=Number(value||0),normalized=(unit||"").toLowerCase();if(normalized==="mg"){amount/=1000;normalized="g"}if(normalized==="g"&&amount>=1000)return`${(amount/1000).toFixed(amount%1000?2:0)} kg`;if(normalized==="ml"&&amount>=1000)return`${(amount/1000).toFixed(amount%1000?2:0)} l`;return`${Number(amount.toFixed(2))} ${normalized||unit}`}
function mealTypeLabel(value:string){return({BREAKFAST:"Desayuno",MID_MORNING:"Media mañana",LUNCH:"Comida",SNACK:"Merienda",DINNER:"Cena",OTHER:"Otra comida"} as Record<string,string>)[value?.toUpperCase()]||value}
function localizeMeal(meal:TodayMeal):TodayMeal{return{...meal,meal_type:({BREAKFAST:"Desayuno",MID_MORNING:"Media mañana",LUNCH:"Comida",SNACK:"Merienda",DINNER:"Cena",OTHER:"Otra comida"} as Record<string,string>)[meal.meal_type?.toUpperCase()]||meal.meal_type}}
function calendarWeeks(){const start=new Date(),day=start.getDay()||7;start.setHours(12,0,0,0);start.setDate(start.getDate()-day+1);return Array.from({length:8},(_,index)=>{const monday=new Date(start);monday.setDate(start.getDate()+index*7);const sunday=new Date(monday);sunday.setDate(monday.getDate()+6);const thursday=new Date(monday);thursday.setDate(monday.getDate()+3);const yearStart=new Date(thursday.getFullYear(),0,1);const number=Math.ceil((((thursday.getTime()-yearStart.getTime())/86400000)+yearStart.getDay()+1)/7);const format=(date:Date)=>date.toLocaleDateString("es",{day:"numeric",month:"short"});return{number,label:`${index===0?"Esta semana · ":""}${format(monday)} – ${format(sunday)}`}})}
