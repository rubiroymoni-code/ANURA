import { useEffect, useState } from "react";
import type { CSSProperties } from "react";
import {
  API_BASE,
  Household,
  householdApi,
  nutritionApi,
  NutritionImportPreview,
  NutritionDashboard,
  MealOption,
  TodayMeal,
} from "./api";
import {
  ChevronDown,
  CalendarDays,
  ChefHat,
  Download,
  FileUp,
  Home,
  Luggage,
  ShoppingBasket,
  Copy,
  MessageCircle,
  Pill,
  SlidersHorizontal,
  Plus,
  Users,
  Utensils,
  Trash2,
  X,
} from "lucide-react";
import { HouseholdView } from "./HouseholdView";
import { SupplementsPanel } from "./SupplementsPanel";
import { NutritionPreferencesPanel } from "./NutritionPreferencesPanel";
import { TravelModePanel } from "./TravelModePanel";

const nutritionPlanStatusLabel:Record<string,string>={ACTIVE:"Activo",SUPERSEDED:"Archivado",DRAFT:"Borrador"};
type NutritionSection="home"|"cook"|"preferences"|"household"|"import"|"shopping"|"supplements"|"travel"|"plan"|"recipe"|"manage"|"meal-locations";
export function NutritionHub({ onClose,onRegisterMeal,onAddMeal,initialSection="home",initialRecipeMeal,onInitialRecipeOpened }: { onClose: () => void;onRegisterMeal?:(meal:TodayMeal)=>void;onAddMeal?:()=>void;initialSection?:NutritionSection;initialRecipeMeal?:TodayMeal|null;onInitialRecipeOpened?:()=>void }) {
  const [section, setSection] = useState<NutritionSection>(initialSection);
  const [sectionHistory,setSectionHistory]=useState<NutritionSection[]>([]);
  const navigate=(next:NutritionSection)=>{if(next===section)return;setSectionHistory(history=>[...history,section]);setSection(next)};
  const goBack=()=>{const previous=sectionHistory.at(-1)||"home";setSectionHistory(history=>history.slice(0,-1));setSection(previous)};
  const previousSection=sectionHistory.at(-1),sectionName=(value?:NutritionSection)=>({home:"Hoy",cook:"Cocina",preferences:"Preferencias",household:"Unidad doméstica",import:"Importación",shopping:"Compra",supplements:"Suplementos",travel:"Modo viaje",plan:"Plan",recipe:"Receta",manage:"Planes y gestión","meal-locations":"Dónde comemos"} as Record<NutritionSection,string>)[value||"home"];
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
  const [mealDate,setMealDate]=useState(new Date().toLocaleDateString("en-CA"));
  const [travelToday,setTravelToday]=useState<Awaited<ReturnType<typeof nutritionApi.travelToday>>>({});
  const activePlan = plans.find((plan) => plan.status === "ACTIVE") || plans[0];
  const planningPlan=[...plans].sort((a,b)=>b.version-a.version)[0];
  const replacementReady=Boolean(activePlan?.valid_until&&plans.some(plan=>plan.id!==activePlan.id&&plan.status==="DRAFT"&&Boolean(plan.valid_from)&&new Date(`${plan.valid_from}T00:00:00`).getTime()<=new Date(`${activePlan.valid_until}T00:00:00`).getTime()+86400000));
  const expiry=nutritionPlanExpiry(activePlan?.valid_until,replacementReady);
  useEffect(() => {
    void nutritionApi.today(mealDate).then(rows=>setTodayMeals(rows.map(localizeMeal))).catch(()=>setTodayMeals([]));
    void nutritionApi.dashboard().then(setDashboard).catch(()=>setDashboard(null));
    void nutritionApi.travelToday(mealDate).then(setTravelToday).catch(()=>setTravelToday({}));
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
  const reloadMeals=async(date=mealDate)=>setTodayMeals((await nutritionApi.today(date)).map(localizeMeal));
  useEffect(()=>{const refresh=()=>{void reloadMeals(mealDate);if(mealDate===new Date().toLocaleDateString("en-CA"))void nutritionApi.dashboard().then(setDashboard)};window.addEventListener("anura:nutrition-changed",refresh);return()=>window.removeEventListener("anura:nutrition-changed",refresh)},[mealDate]);
  useEffect(()=>{if(todayMeals.some(meal=>/^[A-Z_]+$/.test(meal.meal_type)))setTodayMeals(rows=>rows.map(localizeMeal))},[todayMeals]);
  useEffect(()=>{
    if(!initialRecipeMeal||!recipes.length)return;
    const recipeName=initialRecipeMeal.recipe.trim().toLocaleLowerCase("es");
    const recipe=recipes.find(item=>item.name.trim().toLocaleLowerCase("es")===recipeName);
    if(!recipe)return;
    setSelectedRecipe(recipe.id);
    setSelectedMeal(initialRecipeMeal.planned_meal_id);
    setSectionHistory(["home","cook"]);
    setSection("recipe");
    onInitialRecipeOpened?.();
  },[initialRecipeMeal,recipes,onInitialRecipeOpened]);
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
                  : section === "travel"
                    ? "Modo viaje"
                      : section === "plan"
                        ? "Plan nutricional"
                        : section === "manage"
                          ? "Planes y gestión"
                          : "Receta"}
            </h2>
          </div>
          <button onClick={onClose}>
            <X />
          </button>
        </div>
        <nav className="nutrition-primary-nav"><button className={section==="home"?"active":""} onClick={()=>navigate("home")}><Utensils/>Hoy</button><button className={section==="cook"||section==="recipe"?"active":""} onClick={()=>navigate("cook")}><ChefHat/>Cocina</button><button className={section==="shopping"?"active":""} onClick={()=>navigate("shopping")}><ShoppingBasket/>Compra</button><button className={section==="supplements"?"active":""} onClick={()=>navigate("supplements")}><Pill/>Suplementos</button><button className={section==="manage"?"active":""} onClick={()=>navigate("manage")}><FileUp/>Gestión</button></nav>
        <div className="nutrition-hub-content" onClickCapture={(event)=>{const target=(event.target as HTMLElement).closest(".nutrition-today .today-recipe-link");if(!target||!onRegisterMeal)return;event.preventDefault();event.stopPropagation();const buttons=Array.from(event.currentTarget.querySelectorAll(".nutrition-today .today-recipe-link"));const meal=todayMeals[buttons.indexOf(target as HTMLButtonElement)];if(meal)onRegisterMeal({...meal,meal_date:mealDate})}}>
        {loadError && <div className="error" role="alert">{loadError}</div>}
        {expiry.urgent&&<section className="nutrition-expiry-warning" role="alert"><CalendarDays/><span><b>{expiry.expired?"Plan caducado":"Actualiza tu plan"}</b><small>{expiry.message} Genera el prompt con tu progreso e importa la siguiente versión para mantener comidas, macros y compra al día.</small></span></section>}
        {section === "home" && (
          <>
            {dashboard&&<section className={`nutrition-overview ${balanceExpanded?"expanded":""}`}><button className="nutrition-overview-summary" onClick={()=>setBalanceExpanded(value=>!value)}><span><small>BALANCE DE HOY</small><b>{Number(dashboard.consumed.calories||0).toFixed(0)} / {Number(dashboard.target.calories||dashboard.planned.calories||0).toFixed(0)} kcal</b><em>{activePlan?`${activePlan.name} · versión ${activePlan.version}`:"Sin plan activo"}</em></span><strong>{Math.round(Number(dashboard.target.calories||dashboard.planned.calories||0)?Number(dashboard.consumed.calories||0)/Number(dashboard.target.calories||dashboard.planned.calories||1)*100:0)}%</strong><ChevronDown/></button>{balanceExpanded&&<div className="nutrition-overview-detail"><NutritionBalance data={dashboard}/></div>}</section>}
            <section className="nutrition-today"><div><small>{mealDate===new Date().toLocaleDateString("en-CA")?"HOY":"OTRO DÍA"}</small><h3>Lo que te toca comer</h3><p>También puedes completar una comida pendiente de un día anterior.</p><label className="meal-day-picker">Ver día<input type="date" value={mealDate} max={new Date().toLocaleDateString("en-CA")} onChange={event=>{setMealDate(event.target.value);void reloadMeals(event.target.value);void nutritionApi.travelToday(event.target.value).then(setTravelToday)}}/></label></div>{travelToday.id&&<article className="travel-today-card"><Luggage/><span><small>{travelToday.title} · {travelToday.plan_label||"Día flexible"}</small><b>{travelToday.guidance||"Come con flexibilidad y registra solo lo que te resulte útil."}</b><em>No genera compra ni penaliza la adherencia.</em></span></article>}{todayMeals.length?todayMeals.map(meal=><TodayNutritionCard key={meal.planned_meal_id} meal={meal} date={mealDate} refresh={()=>reloadMeals()} open={()=>{const recipe=recipes.find(r=>r.name.trim().toLowerCase()===meal.recipe.trim().toLowerCase());if(recipe){setSelectedRecipe(recipe.id);setSelectedMeal(meal.planned_meal_id);navigate("recipe")}}} toggle={async()=>{if(meal.status==="PENDING")await nutritionApi.completeToday(meal.planned_meal_id,mealDate);else await nutritionApi.undoToday(meal.planned_meal_id,mealDate);await reloadMeals();if(mealDate===new Date().toLocaleDateString("en-CA"))setDashboard(await nutritionApi.dashboard())}}/>):!travelToday.id&&<p>No hay comidas asignadas para ese día en el plan activo.</p>}{onAddMeal&&<button className="nutrition-add-meal" onClick={onAddMeal}><Plus/>Registrar otra comida</button>}</section>
          </>
        )}
        {section === "manage" && <div className="nutrition-tools plan-management"><div className="nutrition-menu">
          <button className="workout-import-action" onClick={() => navigate("import")}><FileUp/><span><b>Importar dieta</b><small>Sube una nueva planificación o versión</small></span></button>
          <NutritionPlanManagement plans={plans} open={id=>{setSelectedPlan(id);navigate("plan")}} refresh={async()=>setPlans(await nutritionApi.plans())}/>
          <section className="managed-plan-section managed-settings-section"><header><small>CONFIGURACIÓN</small><b>Preferencias y hogar</b></header><div className="nutrition-menu">
            <button onClick={() => navigate("meal-locations")}><CalendarDays/><b>Dónde comemos</b><span>Planifica casa u oficina por persona, día y comida</span></button>
            <button onClick={() => navigate("household")}><Users/><b>Mi unidad doméstica</b><span>{households[0]?.name || "Crear o aceptar invitación"}</span></button>
            <button onClick={() => navigate("preferences")}><SlidersHorizontal/><b>Preferencias alimentarias</b><span>Gustos, exclusiones y planificación</span></button>
            <button onClick={() => navigate("travel")}><Luggage/><b>Modo viaje</b><span>Fechas flexibles, criterios y seguimiento</span></button>
          </div></section>
        </div></div>}
        {section === "household" && (
          <HouseholdView
            households={households}
            refresh={() => householdApi.list().then(setHouseholds)}
          />
        )}{" "}
        {section === "import" && <NutritionImport onImported={async()=>{const next=await nutritionApi.plans();setPlans(next);return next}} />}
        {section === "shopping" && <Shopping plans={plans} />}
        {section === "meal-locations" && <MealLocationPlanner plan={planningPlan} />}
        {section === "preferences" && <NutritionPreferencesPanel />}
        {section === "cook" && <CookByDate planId={activePlan?.id} recipes={recipes} open={(recipeId,mealId)=>{setSelectedRecipe(recipeId);setSelectedMeal(mealId);navigate("recipe")}} />}
        {section === "supplements" && <SupplementsPanel />}
        {section === "travel" && <TravelModePanel onChanged={()=>void nutritionApi.travelToday(mealDate).then(setTravelToday)}/>}
        {section === "plan" && selectedPlan && <PlanView id={selectedPlan} status={plans.find(plan=>plan.id===selectedPlan)?.status||""} onDeleted={async()=>{setPlans(await nutritionApi.plans());setSelectedPlan(null);setSectionHistory([]);setSection("home")}} />}
        {section === "recipe" && selectedRecipe && (
          <RecipeView id={selectedRecipe} mealId={selectedMeal} />
        )}
        {section !== "home" && (
          <button className="text-btn" onClick={goBack}>
            ← Volver a {sectionName(previousSection)}
          </button>
        )}
        </div>
      </section>
    </div>
  );
}

function nutritionPlanExpiry(value?:string,replacementReady=false){if(!value||replacementReady)return{urgent:false,expired:false,message:""};const days=Math.ceil((new Date(`${value}T23:59:59`).getTime()-Date.now())/86400000);return{urgent:days<=1,expired:days<0,message:days<0?"El plan ya ha terminado.":days===0?"El plan termina hoy.":"Al plan le queda un día."}}
function NutritionPlanManagement({plans,open,refresh}:{plans:Array<{id:string;name:string;version:number;status:string;valid_from?:string;valid_until?:string}>;open:(id:string)=>void;refresh:()=>Promise<void>}){
 const active=plans.find(plan=>plan.status==="ACTIVE"),drafts=plans.filter(plan=>plan.status==="DRAFT"),past=plans.filter(plan=>plan.status!=="ACTIVE"&&plan.status!=="DRAFT");
 const PlanCard=({plan}:{plan:(typeof plans)[number]})=>{
  const remove=async()=>{if(!confirm("¿Eliminar este plan nutricional? Se borrarán sus días y comidas planificadas. Esta acción no se puede deshacer."))return;await nutritionApi.deletePlan(plan.id);await refresh()};
  return <article><button className="managed-plan-open" onClick={()=>open(plan.id)}><span><small>{plan.status==="ACTIVE"?"Plan actual":plan.status==="DRAFT"?"Próximo plan":"Plan archivado"}</small><b>{plan.name}</b><em>Versión {plan.version}{plan.valid_from?` · Desde ${plan.valid_from}`:""}{plan.valid_until?` · Hasta ${plan.valid_until}`:""}</em></span></button><div className="managed-plan-actions">{plan.status!=="ACTIVE"&&<button className="managed-plan-activate" onClick={async()=>{await nutritionApi.activate(plan.id);await refresh()}}>Activar</button>}<button className="managed-plan-delete" aria-label={`Eliminar ${plan.name}`} onClick={()=>void remove()}><Trash2/><span>Eliminar</span></button></div></article>
 };
 return <><section className="managed-plan-section"><header><small>PLAN ACTUAL</small><b>Lo que se aplica hoy</b></header>{active?<PlanCard plan={active}/>:<p className="managed-plan-empty">No hay un plan activo.</p>}</section>{drafts.length>0&&<section className="managed-plan-section"><header><small>PRÓXIMO PLAN</small><b>Preparado para activarse</b></header>{drafts.map(plan=><PlanCard key={plan.id} plan={plan}/>)}</section>}{past.length>0&&<details className="managed-plan-history"><summary>Historial de planes ({past.length})</summary><div>{past.map(plan=><PlanCard key={plan.id} plan={plan}/>)}</div></details>}</>
}
function TodayNutritionCard({meal,date,refresh,open,toggle}:{meal:TodayMeal;date:string;refresh:()=>Promise<void>;open:()=>void;toggle:()=>Promise<void>}){
  const [options,setOptions]=useState([] as MealOption[]);
  const [optionError,setOptionError]=useState("");
  const [changing,setChanging]=useState(false);
  const substituted=meal.status==="SUBSTITUTED",partial=meal.status==="PARTIAL",plannedCalories=Number(meal.planned_calories??0),fit=substituted?substitutionFit(meal):"";
  useEffect(()=>{setOptionError("");void nutritionApi.mealOptions(meal.planned_meal_id).then(setOptions).catch(()=>{setOptions([]);setOptionError("No se han podido cargar las opciones de esta comida.")})},[meal.planned_meal_id]);
  const changeOption=async(optionCode:string)=>{setChanging(true);try{await nutritionApi.selectMealOption(meal.planned_meal_id,optionCode,date);await refresh()}finally{setChanging(false)}};
  return <article className={`${meal.status!=="PENDING"?"completed":""} ${substituted?`substituted substitution-${fit}`:""}`}>
    <button className="today-recipe-link" onClick={open}><small>{meal.meal_type}{substituted?` · HECHA · ${fit==="aligned"?"MUY AJUSTADA":fit==="close"?"AJUSTADA":"DIFERENTE AL PLAN"}`:partial?` · PARCIAL · ${meal.adherence_percent||0}%`:""}</small>{substituted?<><b>Sustituido por {meal.custom_name||"otra comida"}</b><em className="actual-meal-macros">Real: {Number(meal.calories||0).toFixed(0)} kcal · P {Number(meal.protein||0).toFixed(0)} · C {Number(meal.carbohydrates||0).toFixed(0)} · G {Number(meal.fat||0).toFixed(0)}{meal.actual_portion?` · ${meal.actual_portion}`:""}</em><span className="planned-meal-reference">Previsto: {meal.recipe||meal.meal_name} · {plannedCalories.toFixed(0)} kcal · diferencia {signedCalories(Number(meal.calories||0)-plannedCalories)}</span></>:<><b>{meal.recipe||meal.meal_name}</b><em>{Number(meal.calories||0).toFixed(0)} kcal · P {Number(meal.protein||0).toFixed(0)} · C {Number(meal.carbohydrates||0).toFixed(0)} · G {Number(meal.fat||0).toFixed(0)}</em></>}</button>
    {options.length>1&&meal.status==="PENDING"&&<select className="meal-option-select" value={meal.option_code||"DEFAULT"} disabled={changing} aria-label={`Opción para ${meal.meal_name}`} onChange={event=>void changeOption(event.target.value)}>{options.map(option=><option key={option.option_code} value={option.option_code}>{option.option_label} · {Number(option.calories).toFixed(0)} kcal</option>)}</select>}
    {optionError&&<small className="meal-option-error">{optionError}</small>}
    <button aria-label={meal.status==="PENDING"?`Completar ${meal.meal_name}`:`Deshacer ${meal.meal_name}`} title={meal.status==="PENDING"?"Marcar como hecha":"Deshacer registro"} onClick={()=>void toggle()}>{meal.status!=="PENDING"?"✓":"○"}</button>
  </article>
}
function substitutionFit(meal:TodayMeal){const deviation=(actual:number,planned:number)=>planned>0?Math.abs(actual-planned)/planned:actual>0?1:0,kcal=deviation(Number(meal.calories||0),Number(meal.planned_calories||0)),macros=[deviation(Number(meal.protein||0),Number(meal.planned_protein||0)),deviation(Number(meal.carbohydrates||0),Number(meal.planned_carbohydrates||0)),deviation(Number(meal.fat||0),Number(meal.planned_fat||0))],macro=Math.max(...macros);if(kcal<=.15&&macro<=.25)return"aligned";if(kcal<=.3&&macro<=.45)return"close";return"different"}
function signedCalories(value:number){return `${value>0?"+":""}${value.toFixed(0)} kcal`}
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
function NutritionImport({onImported}:{onImported:()=>Promise<Array<{id:string;name:string;version:number;status:string;valid_from?:string;valid_until?:string}>>}) {
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
  const [stage,setStage]=useState("");
  const activateImported=async(planId?:string)=>{const next=await onImported(),imported=next.find(plan=>plan.id===planId)||next.find(plan=>plan.version===p?.version&&(plan.status==="DRAFT"||plan.status==="ACTIVE"));if(!imported)throw new Error("La dieta se importó, pero no se ha encontrado el plan creado");if(imported.status!=="ACTIVE")await nutritionApi.activate(imported.id);await onImported();setDone(true)};
  const previewFile=async(selected:File)=>{setFile(selected);setP(null);setError("");setBusy(true);setStage("Validando el CSV...");try{const preview=await nutritionApi.preview(type,selected);setP(preview);setStage(preview.confirmable?`Versión ${preview.version} válida y preparada para importar`:"El archivo contiene errores")}catch(cause){setError(cause instanceof Error?cause.message:"No se pudo validar el CSV");setStage("")}finally{setBusy(false)}};
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
            const selected=e.target.files?.[0];
            if(selected)void previewFile(selected);else{setFile(null);setP(null);setStage("")}
          }}
        />
      </label>
      {stage&&<p className="form-note" role="status">{stage}</p>}
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
        disabled={!file || busy || !p?.confirmable}
        onClick={async () => {
          setBusy(true);
          setError("");
          setStage(`Importando y activando la versión ${p?.version}...`);
          try {
            const confirmed=await nutritionApi.confirm(p!.importJobId);
            await activateImported(confirmed.planId);
          } catch (cause) {
            if(p&&cause instanceof Error&&cause.message.includes("Ya existe esa versión")){try{await activateImported()}catch(activationError){setError(activationError instanceof Error?activationError.message:"No se pudo activar la dieta")}}else setError(cause instanceof Error?cause.message:"No se pudo completar la importación");
            setStage("La dieta no se ha activado");
          } finally {
            setBusy(false);
          }
        }}
      >
        {busy
          ? "Procesando..."
          : `Importar y activar${p?.version?` v${p.version}`:" dieta"}`}
      </button>
    </div>
  );
}
function CookToday({planId,recipes,open}:{planId?:string;recipes:Array<{id:string;name:string}>;open:(recipeId:string,mealId:string)=>void}){const [rows,setRows]=useState<Array<Record<string,unknown>>>([]),[selectedDay,setSelectedDay]=useState(new Date().getDay()||7),[loading,setLoading]=useState(false);const currentUser=JSON.parse(localStorage.getItem("anura-user")||"{}");useEffect(()=>{if(!planId){setRows([]);return}setLoading(true);void nutritionApi.week(planId).then(setRows).finally(()=>setLoading(false))},[planId]);const todayDay=new Date().getDay()||7,mine=rows.filter(row=>String(row.user_id)===String(currentUser.id)),days=[...new Map(mine.map(row=>[Number(row.day_number),String(row.day_name||`Día ${row.day_number}`)])).entries()].sort((a,b)=>a[0]-b[0]),dayMeals=mine.filter(row=>Number(row.day_number)===selectedDay).sort((a,b)=>Number(a.meal_order||0)-Number(b.meal_order||0));return <div className="cook-workspace"><BatchCooking planId={planId}/><section className="cook-today"><div className="food-pref-hero"><ChefHat/><span><small>COCINA DEL DÍA</small><h3>{selectedDay===todayDay?"Preparaciones de hoy":`Preparaciones del ${days.find(([day])=>day===selectedDay)?.[1]?.toLowerCase()||`día ${selectedDay}`}`}</h3><p>Abre una receta para ver el reparto exacto de cada comida.</p></span></div>{days.length>0&&<div className="plan-day-tabs">{days.map(([day,name])=><button key={day} className={selectedDay===day?"active":""} onClick={()=>setSelectedDay(day)}><b>{day===todayDay?"Hoy":name.slice(0,3)}</b><small>Día {day}</small></button>)}</div>}{loading?<div className="empty">Cargando comidas…</div>:dayMeals.length?dayMeals.map(meal=>{const recipe=recipes.find(item=>item.name.trim().toLocaleLowerCase("es")===String(meal.recipe).trim().toLocaleLowerCase("es"));return <button key={String(meal.planned_meal_id)} disabled={!recipe} onClick={()=>recipe&&open(recipe.id,String(meal.planned_meal_id))}><span><small>{mealTypeLabel(String(meal.meal_type))}</small><b>{String(meal.recipe)}</b><em>{Number(meal.calories||0).toFixed(0)} kcal para ti</em></span><strong>Ver reparto →</strong></button>}):<div className="empty">No hay preparaciones asignadas para este día.</div>}</section></div>}

function MealLocationPlanner({plan}:{plan?:{id:string;name:string;version:number;status:string;valid_from?:string;valid_until?:string}}){
  const [rows,setRows]=useState<Array<Record<string,unknown>>>([]),[week,setWeek]=useState(1),[day,setDay]=useState(new Date().getDay()||7),[saving,setSaving]=useState(""),[message,setMessage]=useState("");
  const load=async()=>{if(!plan){setRows([]);return}setRows(await nutritionApi.planMealOptions(plan.id))};
  useEffect(()=>{void load()},[plan?.id]);
  const weeks=[...new Set(rows.map(row=>Number(row.week_number)))].sort((a,b)=>a-b),days=[...new Map(rows.filter(row=>Number(row.week_number)===week).map(row=>[Number(row.day_number),String(row.day_name)])).entries()].sort((a,b)=>a[0]-b[0]);
  useEffect(()=>{if(weeks.length&&!weeks.includes(week))setWeek(weeks[0])},[rows]);
  useEffect(()=>{if(days.length&&!days.some(([value])=>value===day))setDay(days[0][0])},[week,rows]);
  const visible=rows.filter(row=>Number(row.week_number)===week&&Number(row.day_number)===day),people=[...new Map(visible.map(row=>[String(row.user_id),String(row.display_name)])).entries()];
  const choose=async(row:Record<string,unknown>)=>{if(!plan)return;const key=`${row.user_id}-${row.option_group}`;setSaving(key);setMessage("");try{await nutritionApi.selectMealOption(String(row.planned_meal_id),String(row.option_code),String(row.meal_date),String(row.user_id));await load();try{await nutritionApi.generateShopping(plan.id,week)}catch{setMessage("Plan guardado. La lista de compra tiene cambios manuales: actualízala desde Compra para conservar el control.")}}finally{setSaving("")}};
  if(!plan)return <div className="empty">Activa un plan para organizar dónde come cada persona.</div>;
  return <section className="meal-location-planner"><header><CalendarDays/><span><small>PLANIFICACIÓN FAMILIAR · VERSIÓN {plan.version}</small><h3>Dónde comemos</h3><p>{plan.name}</p></span>{plan.status!=="ACTIVE"&&<button className="primary" onClick={async()=>{await nutritionApi.activate(plan.id);location.reload()}}>Activar esta dieta</button>}</header><div className="location-week-tabs">{weeks.map(value=><button key={value} className={week===value?"active":""} onClick={()=>setWeek(value)}>Semana {value}</button>)}</div><div className="plan-day-tabs">{days.map(([value,name])=><button key={value} className={day===value?"active":""} onClick={()=>setDay(value)}><b>{name.slice(0,3)}</b><small>Día {value}</small></button>)}</div>{message&&<p className="form-note">{message}</p>}<div className="location-people">{people.map(([userId,name])=><article key={userId}><header><Users/><b>{name}</b></header><div>{[...new Map(visible.filter(row=>String(row.user_id)===userId).map(row=>[Number(row.meal_order),visible.filter(item=>String(item.user_id)===userId&&Number(item.meal_order)===Number(row.meal_order))])).values()].map(options=>{const selected=options.find(row=>Boolean(row.selected))||options[0],key=`${userId}-${selected.option_group}`;return <section key={Number(selected.meal_order)}><span><small>{mealTypeLabel(String(selected.meal_type))}</small><b>{String(selected.recipe)}</b></span>{options.length>1?<div>{options.map(option=><button key={String(option.option_code)} className={Boolean(option.selected)?"active":""} disabled={saving===key} onClick={()=>void choose(option)}>{String(option.option_label)}</button>)}</div>:<em>Comida fija</em>}</section>})}</div></article>)}</div></section>;
}

function CookByDate({planId,recipes,open}:{planId?:string;recipes:Array<{id:string;name:string}>;open:(recipeId:string,mealId:string)=>void}) {
  const [rows,setRows]=useState<Array<Record<string,unknown>>>([]);
  const [selectedDay,setSelectedDay]=useState<number|null>(null);
  const [loading,setLoading]=useState(false);
  const reload=async()=>{if(!planId){setRows([]);return}setLoading(true);try{setRows(await nutritionApi.week(planId))}finally{setLoading(false)}};
  useEffect(()=>{void reload()},[planId]);
  const days=[...new Map(rows.map(row=>[Number(row.day_number),String(row.day_name||`D\u00eda ${row.day_number}`)])).entries()].sort((a,b)=>a[0]-b[0]);
  const todayDay=new Date().getDay()||7;
  const visibleDay=selectedDay??todayDay;
  const dayMeals=rows.filter(row=>Number(row.day_number)===visibleDay).sort((a,b)=>Number(a.meal_order||0)-Number(b.meal_order||0));
  const mealGroups=[...new Map(dayMeals.map(row=>[Number(row.meal_order),dayMeals.filter(item=>Number(item.meal_order)===Number(row.meal_order))])).values()];
  return <div className="cook-workspace"><BatchCooking planId={planId}/><section className="cook-today"><div className="food-pref-hero"><ChefHat/><span><small>{"COCINA DEL D\u00cdA"}</small><h3>{visibleDay===todayDay?"Preparaciones de hoy":`Preparaciones del ${days.find(([day])=>day===visibleDay)?.[1]?.toLowerCase()||`d\u00eda ${visibleDay}`}`}</h3><p>Cuatro comidas, con la preparaci\u00f3n de cada persona dentro.</p></span></div>{days.length>0&&<div className="plan-day-tabs">{days.map(([day,name])=><button key={day} className={visibleDay===day?"active":""} onClick={()=>setSelectedDay(day)}><b>{day===todayDay?"Hoy":name.slice(0,3)}</b><small>{"D\u00eda "}{day}</small></button>)}</div>}{loading?<div className="empty">Cargando comidas...</div>:mealGroups.length?mealGroups.map(group=><CookMealGroup key={Number(group[0]?.meal_order)} meals={group} recipes={recipes} open={open} reload={reload}/>):<div className="empty">{"No hay comidas asignadas para este d\u00eda."}</div>}</section></div>;
}
function CookMealGroup({meals,recipes,open,reload}:{meals:Array<Record<string,unknown>>;recipes:Array<{id:string;name:string}>;open:(recipeId:string,mealId:string)=>void;reload:()=>Promise<void>}){
  const first=meals[0];
  const preparations=[...new Map(meals.map(meal=>[String(meal.recipe).trim().toLocaleLowerCase("es"),meals.filter(item=>String(item.recipe).trim().toLocaleLowerCase("es")===String(meal.recipe).trim().toLocaleLowerCase("es"))])).values()];
  return <article className="cook-meal-group"><header><small>{mealTypeLabel(String(first.meal_type))}</small><b>{preparations.length===1?"1 preparación conjunta":`${preparations.length} preparaciones`}</b></header><div>{preparations.map(group=>{const meal=group[0];return <button key={String(meal.recipe)} onClick={()=>open(String(meal.recipe_id),String(meal.planned_meal_id))}><span><b>{String(meal.recipe)}</b><small>Para {group.map(item=>String(item.display_name)).join(" y ")}</small><em>{group.map(item=>`${String(item.display_name)} ${Number(item.calories||0).toFixed(0)} kcal`).join(" · ")}</em></span><strong>Ver receta</strong></button>})}</div></article>;
}
function normalizeWeekday(value:string){return value.normalize("NFD").replace(/[\u0300-\u036f]/g,"").trim().toLocaleLowerCase("es")}

type PrepRow={planned_meal_id:string;day_number:number;day_name:string;meal_type:string;meal_name:string;recipe:string;user_id:string;display_name:string;ingredients:unknown};
type PrepIngredient={name:string;quantity:number;unit:string};
function BatchCooking({planId}:{planId?:string}){const [rows,setRows]=useState<PrepRow[]>([]),[period,setPeriod]=useState<"remaining"|"week">("week"),[open,setOpen]=useState(false),[loading,setLoading]=useState(false);useEffect(()=>{if(!planId){setRows([]);return}setLoading(true);void nutritionApi.week(planId).then(value=>setRows(value as unknown as PrepRow[])).finally(()=>setLoading(false))},[planId]);const today=new Date().getDay()||7,selected=rows.filter(row=>period==="week"||Number(row.day_number)>=today),ingredients=batchIngredients(selected),containers=batchContainers(selected);return <section className={`batch-cooking ${open?"open":""}`}><button className="batch-cooking-head" onClick={()=>setOpen(value=>!value)}><span className="batch-cooking-icon"><ChefHat/></span><span><small>PREPARAR DE UNA VEZ</small><b>Organiza las raciones de la unidad</b><em>{loading?"Calculando…":rows.length?`${ingredients.prep.length} preparaciones · ${containers.length} recipientes`:"Necesitas un plan activo"}</em></span><strong>{open?"Cerrar":"Preparar semana"}</strong></button>{open&&<div className="batch-cooking-body"><div className="batch-period"><button className={period==="week"?"active":""} onClick={()=>setPeriod("week")}>Toda la semana</button><button className={period==="remaining"?"active":""} onClick={()=>setPeriod("remaining")}>Desde hoy</button></div><p className="batch-intro"><b>Orden recomendado:</b> pesa en crudo, cocina cada ingrediente en una tanda, vuelve a pesar si quieres máxima precisión y reparte por recipientes siguiendo las tarjetas.</p>{ingredients.prep.length?<><h3>1. Cocina estas cantidades</h3><div className="batch-totals">{ingredients.prep.map(item=>{const factor=cookingFactor(item.name,item.unit),cooked=factor?item.quantity*factor:null,people=batchPeople(selected,item);return <article key={`${item.name}-${item.unit}`}><header><span><b>{item.name}</b><small>Total conjunto · crudo/seco</small></span><strong>{batchQuantity(item.quantity,item.unit)}</strong></header>{cooked!=null&&<p>Rendimiento aproximado: <b>≈ {batchQuantity(cooked,"g")} cocinado</b></p>}<div>{people.map(person=><span key={person.name}><b>{person.name}</b><small>{batchQuantity(person.quantity,item.unit)}{factor?` → ≈ ${batchQuantity(person.quantity*factor,"g")}`:""}</small></span>)}</div></article>})}</div><h3>2. Reparte y etiqueta</h3><div className="batch-containers">{containers.map(container=><article key={container.key}><header><span><small>{container.day} · {mealTypeLabel(container.mealType)}</small><b>{container.person}</b><em>{container.recipe}</em></span><strong>RECIPIENTE</strong></header><div>{container.ingredients.map(item=>{const factor=cookingFactor(item.name,item.unit);return <span key={`${item.name}-${item.unit}`}><b>{item.name}</b><small>{factor?`≈ ${batchQuantity(item.quantity*factor,"g")} cocinado`:batchQuantity(item.quantity,item.unit)}</small></span>})}</div></article>)}</div></>:<div className="empty">No hay ingredientes adecuados para preparar por tandas en este periodo.</div>}{ingredients.fresh.length>0&&<details className="batch-fresh"><summary>Preparar al momento ({ingredients.fresh.length})</summary><p>Fruta, lácteos, pan, aguacate, aliños y otros alimentos que suelen conservarse mejor sin montar con antelación.</p><div>{ingredients.fresh.map(item=><span key={`${item.name}-${item.unit}`}><b>{item.name}</b><small>{batchQuantity(item.quantity,item.unit)} para el periodo</small></span>)}</div></details>}<p className="batch-safety"><b>Conservación práctica:</b> deja enfriar antes de cerrar, guarda en frío y congela las raciones que no consumirás en 3 días. Las equivalencias cocinadas son aproximadas.</p></div>}</section>}

function batchIngredients(rows:PrepRow[]){const totals=new Map<string,PrepIngredient>();rows.forEach(row=>ingredientRows(row.ingredients).forEach(item=>{const name=batchIngredientName(item.name),key=`${name.toLocaleLowerCase("es")}|${item.unit}`,current=totals.get(key);totals.set(key,{name,unit:item.unit,quantity:(current?.quantity||0)+item.quantity})}));const all=[...totals.values()].sort((a,b)=>b.quantity-a.quantity),prep=all.filter(item=>cookingFactor(item.name,item.unit)!=null),fresh=all.filter(item=>cookingFactor(item.name,item.unit)==null);return{prep,fresh}}
function batchIngredientName(name:string){return name.replace(/\s+(crudo|cruda|crudos|crudas|en seco)$/i,"").trim()}
function batchPeople(rows:PrepRow[],target:PrepIngredient){const people=new Map<string,number>();rows.forEach(row=>ingredientRows(row.ingredients).forEach(item=>{if(batchIngredientName(item.name).toLocaleLowerCase("es")===target.name.toLocaleLowerCase("es")&&item.unit===target.unit)people.set(row.display_name,(people.get(row.display_name)||0)+item.quantity)}));return[...people].map(([name,quantity])=>({name,quantity}))}
function batchContainers(rows:PrepRow[]){return rows.map(row=>({key:`${row.planned_meal_id}-${row.user_id}`,person:row.display_name,day:row.day_name,mealType:row.meal_type,recipe:row.recipe,ingredients:ingredientRows(row.ingredients).map(item=>({...item,name:batchIngredientName(item.name)})).filter(item=>cookingFactor(item.name,item.unit)!=null)})).filter(row=>row.ingredients.length>0)}
function batchQuantity(quantity:number,unit:string){if(/^g/i.test(unit)&&quantity>=1000)return`${(quantity/1000).toLocaleString("es",{maximumFractionDigits:2})} kg`;if(/^ml/i.test(unit)&&quantity>=1000)return`${(quantity/1000).toLocaleString("es",{maximumFractionDigits:2})} l`;return`${Math.round(quantity)} ${unit}`}

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
        <article className="recipe-combined"><header><b>Total para cocinar · crudo/seco</b><span>{portions.reduce((sum,p)=>sum+Number(p.quantity||0),0).toFixed(0)} g</span></header><div className="portion-ingredients combined">{combinedIngredients(portions).map((ingredient,index)=><span key={`${ingredient.name}-${index}`}><b>{ingredient.name}</b><em>{ingredientQuantityLabel(ingredient.name,ingredient.quantity,ingredient.unit)}</em></span>)}</div></article>
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
  const [travelDays,setTravelDays]=useState<Array<{id:string;title:string;travel_date:string;plan_label:string;guidance:string}>>([]);
  const [selectedDay,setSelectedDay]=useState(new Date().getDay()||7);
  const [expandedMeal,setExpandedMeal]=useState<string|null>(null);
  const currentUser = JSON.parse(localStorage.getItem("anura-user") || "{}");
  useEffect(() => {
    void nutritionApi.week(id).then(setRows);
    const monday=startOfCurrentWeek(),sunday=new Date(monday);sunday.setDate(monday.getDate()+6);
    void nutritionApi.travelCalendar(dateInput(monday),dateInput(sunday)).then(setTravelDays).catch(()=>setTravelDays([]));
  }, [id]);
  const todayDay=new Date().getDay()||7;
  const mine=rows.filter(row=>String(row.user_id)===String(currentUser.id));
  const travelByDay=new Map(travelDays.map(day=>[isoDay(day.travel_date),day]));
  const days=[...new Map([...mine.map(row=>[Number(row.day_number),String(row.day_name||`Día ${row.day_number}`)] as [number,string]),...travelDays.map(day=>[isoDay(day.travel_date),weekdayName(day.travel_date)] as [number,string])]).entries()].sort((a,b)=>a[0]-b[0]);
  const dayRows=mine.filter(row=>selectedDay===0||Number(row.day_number)===selectedDay).sort((a,b)=>Number(a.day_number)-Number(b.day_number)||Number(a.meal_order||0)-Number(b.meal_order||0));
  const visibleTravel=selectedDay===0?travelDays:(travelByDay.has(selectedDay)?[travelByDay.get(selectedDay)!]:[]);
  return (
    <div className="plan-agenda">
      <div className="plan-day-tabs">{days.map(([day,name])=><button key={day} className={selectedDay===day?"active":""} onClick={()=>setSelectedDay(day)}><b>{day===todayDay?"Hoy":name.slice(0,3)}</b><small>Día {day}</small></button>)}<button className={selectedDay===0?"active":""} onClick={()=>setSelectedDay(0)}><b>Todo</b><small>semana</small></button></div>
      <div className="plan-agenda-intro"><CalendarDays/><span><b>{selectedDay===todayDay?"Tu menú de hoy":selectedDay===0?"Tu semana completa":`Tu menú del día ${selectedDay}`}</b><small>Para cantidades conjuntas y reparto abre Cocina.</small></span></div>
      {!dayRows.length&&!visibleTravel.length&&<div className="empty">No tienes comidas asignadas para este día.</div>}
      <div className="plan-agenda-list">{dayRows.map((meal,index)=>{const key=String(meal.planned_meal_id||`${meal.meal_name}-${index}`);return <PlanMealCard key={key} meal={meal} dayVisible={selectedDay===0} open={expandedMeal===key} toggle={()=>setExpandedMeal(current=>current===key?null:key)}/>})}{visibleTravel.map(day=><TravelPlanDay key={day.travel_date} day={day} showDay={selectedDay===0}/>)}</div>
      <div className="plan-actions"><span className={status==="ACTIVE"?"active":""}>{status==="ACTIVE"?"● Plan activo":nutritionPlanStatusLabel[status||""]||status||"Plan"}</span>{status!=="ACTIVE"&&<button className="primary" onClick={async()=>{await nutritionApi.activate(id);location.reload()}}>Activar este plan</button>}<button className="danger" onClick={async()=>{if(confirm("¿Eliminar este plan nutricional? Se borrarán sus días y comidas planificadas. Esta acción no se puede deshacer.")){await nutritionApi.deletePlan(id);onDeleted()}}}>Eliminar plan</button></div>
    </div>
  );
}

function startOfCurrentWeek(){const date=new Date(),day=date.getDay()||7;date.setHours(12,0,0,0);date.setDate(date.getDate()-day+1);return date}
function dateInput(date:Date){return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,"0")}-${String(date.getDate()).padStart(2,"0")}`}
function isoDay(value:string){return new Date(`${value}T12:00:00`).getDay()||7}
function weekdayName(value:string){return new Date(`${value}T12:00:00`).toLocaleDateString("es",{weekday:"long"})}
function TravelPlanDay({day,showDay}:{day:{title:string;travel_date:string;plan_label:string;guidance:string};showDay:boolean}){return <article className="plan-travel-day"><span><Luggage/><small>{showDay?`${weekdayName(day.travel_date)} · `:""}{day.title}</small></span><h3>{day.plan_label||"Comer fuera"}</h3><p>{day.guidance}</p><footer><b>Día flexible planificado</b><em>Sin compra · fuera de adherencia</em></footer></article>}

function PlanMealCard({meal,dayVisible,open,toggle}:{meal:Record<string,unknown>;dayVisible:boolean;open:boolean;toggle:()=>void}){const ingredients=ingredientRows(meal.ingredients);return <article className={`plan-meal-card ${open?"open":""}`}><button className="plan-meal-summary" onClick={toggle} aria-expanded={open}><span className="agenda-time"><small>{mealTypeLabel(String(meal.meal_type))}</small><b>{String(meal.meal_name)}</b><em>{String(meal.recipe)}</em></span><span className="agenda-macros"><b>{Number(meal.calories||0).toFixed(0)} kcal</b><small>P {Number(meal.protein||0).toFixed(0)} · C {Number(meal.carbohydrates||0).toFixed(0)} · G {Number(meal.fat||0).toFixed(0)}</small></span><ChevronDown/>{dayVisible&&<i>Día {String(meal.day_number)}</i>}</button>{open&&<div className="plan-meal-ingredients"><small>INGREDIENTES · TU CANTIDAD</small>{ingredients.length?<div>{ingredients.map((ingredient,index)=><span key={`${ingredient.name}-${index}`}><b>{ingredient.name}</b><em>{displayQuantity(ingredient.quantity,ingredient.unit)}</em></span>)}</div>:<p>No hay ingredientes detallados para esta comida.</p>}<p>Para ver el total conjunto y el reparto de cada persona, abre <b>Cocina</b>.</p></div>}</article>}

function ingredientRows(value:unknown):Array<{name:string;quantity:number;unit:string}>{if(Array.isArray(value))return value as Array<{name:string;quantity:number;unit:string}>;if(typeof value==="string")try{const parsed=JSON.parse(value);return Array.isArray(parsed)?parsed:[]}catch{return[]}return[]}
function combinedIngredients(people:Array<Record<string,unknown>>){const totals=new Map<string,{name:string;quantity:number;unit:string}>();people.forEach(person=>ingredientRows(person.ingredients).forEach(ingredient=>{const key=`${ingredient.name}|${ingredient.unit}`,current=totals.get(key);totals.set(key,{name:ingredient.name,unit:ingredient.unit||"g",quantity:(current?.quantity||0)+Number(ingredient.quantity||0)})}));return [...totals.values()]}

type PortionIngredient={name:string;quantity:number;unit:string};
function PortionCard({person}:{person:Record<string,unknown>}){const ingredients=ingredientRows(person.ingredients),cooked=cookedIngredientRows(ingredients);return <article><header><b>{String(person.display_name)}</b><span>{Number(person.quantity||0).toFixed(0)} g · {Number(person.calories||0).toFixed(0)} kcal</span></header><div className="portion-ingredients">{ingredients.map((ingredient,index)=><span key={`${ingredient.name}-${index}`}><b>{ingredient.name}</b><em>{ingredientQuantityLabel(ingredient.name,ingredient.quantity,ingredient.unit)}</em></span>)}</div>{cooked.length>0&&<div className="cooked-portion"><small>PARA SERVIR · PESO YA COCINADO</small><div className="portion-ingredients cooked">{cooked.map((ingredient,index)=><span key={`${ingredient.name}-${index}`}><b>{ingredient.name}</b><em>≈ {ingredient.quantity.toFixed(0)} g</em></span>)}</div></div>}</article>}
function ingredientQuantityLabel(name:string,quantity:number,unit:string){const normalized=name.toLocaleLowerCase("es"),grams=/^mg$/i.test(unit)?quantity/1000:/^kg$/i.test(unit)?quantity*1000:/^g(r|ramo|ramos)?$/i.test(unit)?quantity:null;if(grams!=null&&/(^|\s)huevos?(\s+entero)?(\s|$)/.test(normalized)){const raw=grams/60,rounded=Math.abs(raw-Math.round(raw))<.12?Math.round(raw):Math.abs(raw*2-Math.round(raw*2))<.15?Math.round(raw*2)/2:Math.round(raw*10)/10;return `≈ ${rounded.toLocaleString("es")} ${rounded===1?"huevo":"huevos"} · ${Math.round(grams)} g`}return `${Number(quantity).toFixed(0)} ${unit}`}
function cookedIngredientRows(rows:PortionIngredient[]){return rows.flatMap(row=>{const factor=cookingFactor(row.name,row.unit);return factor?[{...row,quantity:Number(row.quantity||0)*factor,unit:"g"}]:[]})}
function cookingFactor(name:string,unit:string){if(!/^g(r|ramo|ramos)?$/i.test((unit||"g").trim()))return null;const value=name.toLocaleLowerCase("es");if(/cocid|cocinad|asado|plancha|hervid|preparad/.test(value))return null;if(/arroz/.test(value))return 2.8;if(/pasta|macarr|espaguet|tallar/.test(value))return 2.4;if(/quinoa/.test(value))return 3;if(/cusc[uú]s|couscous/.test(value))return 2.5;if(/lentej/.test(value))return 2.5;if(/garbanz/.test(value))return 2.4;if(/alubia|jud[ií]a|frijol/.test(value))return 2.5;if(/avena/.test(value))return 3;if(/pollo|pavo/.test(value))return .75;if(/ternera|vacuno|cerdo/.test(value))return .72;if(/salm[oó]n|merluza|at[uú]n|bacalao|pescado/.test(value))return .8;if(/br[oó]coli|coliflor|calabac[ií]n|berenjena|verdura/.test(value))return .85;return null}

function Shopping({ plans }: { plans: Array<{ id: string;name:string;status:string }> }) {
  const weekOptions=calendarWeeks();
  const activePlan=plans.find(p=>p.status==="ACTIVE")||plans[0];
  const [lists,setLists]=useState<Awaited<ReturnType<typeof nutritionApi.shopping>>>([]),[items,setItems]=useState<Awaited<ReturnType<typeof nutritionApi.items>>>([]),[week,setWeek]=useState(weekOptions[0].number),[listId,setListId]=useState(""),[error,setError]=useState(""),[planId,setPlanId]=useState(activePlan?.id||""),[filtersOpen,setFiltersOpen]=useState(false),[generating,setGenerating]=useState(false);
  const [pantry,setPantry]=useState<Awaited<ReturnType<typeof nutritionApi.pantry>>>([]),[pantryOpen,setPantryOpen]=useState(false);
  const selectedPlan=plans.find(plan=>plan.id===planId)||activePlan;
  const load=async(preferred?:string)=>{const next=await nutritionApi.shopping();setLists(next);const selected=next.find(x=>x.id===preferred)||next.find(x=>x.nutrition_plan_id===selectedPlan?.id&&x.week_number===week);setListId(selected?.id||"");setItems(selected?await nutritionApi.items(selected.id):[]);window.dispatchEvent(new Event("anura:reminders-changed"))};
  const loadPantry=async()=>setPantry(await nutritionApi.pantry());
  useEffect(()=>{void load();void loadPantry()},[]);
  const generate=async()=>{if(!selectedPlan||generating)return;setGenerating(true);setError("");try{const result=await nutritionApi.generateShopping(selectedPlan.id,week);await load(result.id);setFiltersOpen(false)}catch(cause){if(cause instanceof Error&&cause.message.includes("modificada")&&confirm("La lista tiene cambios manuales. ¿Regenerarla igualmente?")){const result=await nutritionApi.generateShopping(selectedPlan.id,week,true);await load(result.id)}else setError(cause instanceof Error?cause.message:"No se pudo generar")}finally{setGenerating(false)}};
  const refreshItems=async()=>{if(listId)setItems(await nutritionApi.items(listId));window.dispatchEvent(new Event("anura:reminders-changed"))};
  const categories=[...new Set(items.map(i=>i.category||"OTHER"))];
  return <div className="shopping-view">
    <div className="shopping-toolbar"><button type="button" className="secondary-action" onClick={()=>setFiltersOpen(value=>!value)}>{filtersOpen?"Ocultar filtros":"Filtros de compra"}</button>{filtersOpen&&<div className="shopping-filter-fields"><label>Plan de compra<select value={selectedPlan?.id||""} onChange={async event=>{const nextPlanId=event.target.value;setPlanId(nextPlanId);const list=lists.find(x=>x.nutrition_plan_id===nextPlanId&&x.week_number===week);setListId(list?.id||"");setItems(list?await nutritionApi.items(list.id):[]);}}>{plans.map(plan=><option key={plan.id} value={plan.id}>{plan.name}{plan.status==="DRAFT"?" · Próximo borrador":plan.status==="ACTIVE"?" · Activo":" · Archivado"}</option>)}</select></label><label>Periodo de compra<select value={week} onChange={async e=>{const selectedWeek=Number(e.target.value);setWeek(selectedWeek);const list=lists.find(x=>x.nutrition_plan_id===selectedPlan?.id&&x.week_number===selectedWeek);setListId(list?.id||"");setItems(list?await nutritionApi.items(list.id):[]);}}>{weekOptions.map(option=><option key={option.number} value={option.number}>{option.label}</option>)}</select></label></div>}<button className="primary" disabled={!selectedPlan||generating} onClick={generate}>{generating?"Generando…":lists.some(x=>x.nutrition_plan_id===selectedPlan?.id&&x.week_number===week)?"Actualizar lista":"Generar lista"}</button></div>
    <div className="shopping-management"><button onClick={()=>setPantryOpen(value=>!value)}>{pantryOpen?"Cerrar despensa":`Ver despensa (${pantry.length})`}</button>{listId&&<button onClick={async()=>{if(confirm("¿Vaciar esta lista? Podrás generarla de nuevo desde el plan activo.")){await nutritionApi.resetShopping(listId);await load()}}}>Vaciar lista actual</button>}</div>
    {pantryOpen&&<section className="pantry-manager"><header><div><small>INVENTARIO DOMÉSTICO</small><h3>Saldo previsto en casa</h3></div>{pantry.length>0&&<button className="danger" onClick={async()=>{if(confirm("¿Vaciar toda la despensa doméstica?")){await nutritionApi.clearPantry();await loadPantry()}}}>Vaciar despensa</button>}</header><p className="pantry-explanation"><b>¿Qué significa esta cantidad?</b> Es lo que quedará después de consumir lo reservado para las listas semanales que ya has generado. Al guardar existencias, ANURA actualiza la lista para evitar compras duplicadas.</p>{pantry.length===0?<p className="empty">La despensa está vacía.</p>:pantry.map(item=><article key={`${item.ingredient_id}-${item.unit}`}><div className="pantry-product"><b>{item.name}</b><small>Quedará tras esta semana</small></div><label><input aria-label={`Saldo previsto de ${item.name}`} type="number" min="0" step="0.001" value={item.quantity} onChange={e=>setPantry(current=>current.map(row=>row===item?{...row,quantity:Number(e.target.value)}:row))}/><span>{item.unit}</span></label><button onClick={async()=>{await nutritionApi.updatePantry(item.ingredient_id,item.quantity,item.unit);await generate();await loadPantry()}}>Guardar</button><button className="danger-link" onClick={async()=>{await nutritionApi.deletePantry(item.ingredient_id,item.unit);await generate();await loadPantry()}}>Eliminar</button></article>)}<PantryAddForm saved={async item=>{setPantry(current=>{const existing=current.find(row=>row.ingredient_id===item.ingredient_id&&row.unit===item.unit);return existing?current.map(row=>row===existing?{...row,quantity:Number(row.quantity)+item.quantity}:row):[...current,item]});await generate();await loadPantry()}}/></section>}
    {error&&<p className="error">{error}</p>}
    {!listId?<div className="empty">Genera la lista de la semana desde tu plan activo.</div>:<>
      <div className="shopping-export prominent"><button className="primary secondary" onClick={()=>navigator.clipboard.writeText(items.filter(i=>!i.purchased&&i.quantity>0).map(i=>`${i.name}: ${displayQuantity(i.quantity,i.unit)}`).join("\n"))}>Copiar lista</button><button className="primary" onClick={()=>{void navigator.clipboard.writeText(`Lista ANURA · ${weekOptions.find(x=>x.number===week)?.label}\n\n${items.filter(i=>!i.purchased&&i.quantity>0).map(i=>`☐ ${i.name}: ${displayQuantity(i.quantity,i.unit)}`).join("\n")}`);window.open("https://keep.google.com/#home","_blank","noopener,noreferrer")}}>Abrir en Google Keep</button></div>
      <div className="pantry-note"><b>Cómo funciona la despensa:</b> al generar la lista, ANURA reserva lo que usarás esta semana. La despensa muestra el saldo que quedará después; indica lo que compras realmente y marca el check para añadir cualquier sobrante futuro.</div>
      {categories.map(category=><section className="shopping-category" key={category}><h4>{categoryLabel(category)}</h4>{items.filter(i=>(i.category||"OTHER")===category).map(i=>{const covered=i.quantity<=0&&i.pantry_used>=i.required_quantity;return <article className={`shopping-item ${i.purchased?"purchased":""} ${covered?"covered":""}`} key={i.id}>
        {covered?<span className="shopping-check">✓</span>:<button className="shopping-check" onClick={async event=>{const input=event.currentTarget.parentElement?.querySelector<HTMLInputElement>("input");if(input)await nutritionApi.shoppingQuantity(i.id,Number(input.value));await nutritionApi.toggle(i.id);await refreshItems()}}>{i.purchased?"✓":"○"}</button>}<div><b>{i.name}</b><small>{covered?`Cubierto por despensa · ${displayQuantity(i.pantry_used,i.unit)} disponibles`:`Necesario: ${displayQuantity(i.required_quantity,i.unit)}${i.pantry_used>0?` · Ya disponible: ${displayQuantity(i.pantry_used,i.unit)}`:""}`}</small></div>{covered?<strong className="pantry-covered">No comprar</strong>:<label>Comprado<input type="number" min="0" step="0.001" defaultValue={i.quantity}/><span>{i.unit}</span></label>}
        {i.manual&&<button type="button" className="shopping-item-delete" aria-label={`Eliminar ${i.name}`} onClick={async()=>{if(confirm(`¿Eliminar ${i.name} de la lista?`)){await nutritionApi.deleteShoppingItem(i.id);await refreshItems()}}}><Trash2/>Eliminar</button>}
      </article>})}</section>)}
      <form className="shopping-add" onSubmit={async e=>{e.preventDefault();const f=new FormData(e.currentTarget);await nutritionApi.addShoppingItem(listId,{name:String(f.get("name")),category:String(f.get("category")),quantity:Number(f.get("quantity")),unit:String(f.get("unit"))});e.currentTarget.reset();await refreshItems()}}><b>Añadir a la casa</b><input name="name" required placeholder="Producto"/><select name="category"><option value="OTHER">Otros</option><option value="PANTRY">Despensa</option><option value="FRUIT_VEGETABLES">Fruta y verdura</option><option value="MEAT_FISH">Carnes y pescados</option><option value="DAIRY">Lácteos</option></select><input name="quantity" required type="number" min="0" step="0.001" placeholder="Cantidad"/><UnitSelect/><button className="primary">Añadir</button></form>
    </>}
  </div>;
}
function categoryLabel(value:string){return ({FRUIT_VEGETABLES:"Fruta y verdura",FRUTA:"Fruta",VERDURA:"Verdura",MEAT_FISH:"Carnes y pescados",PROTEINA:"Proteínas",EGGS:"Huevos",HUEVO:"Huevos",DAIRY:"Lácteos",LACTEO:"Lácteos",CEREALS_LEGUMES:"Cereales y legumbres",CEREAL:"Cereales",LEGUMBRE:"Legumbres",FROZEN:"Congelados",PANTRY:"Despensa",DESPENSA:"Despensa",DRINKS:"Bebidas",BEBIDA:"Bebidas",FRUTO_SECO:"Frutos secos",OTHER:"Otros"} as Record<string,string>)[value]||value}
type IngredientSuggestion={name:string;category:string;unit:string;source:"CATALOG"|"SUGGESTED"};
function PantryAddForm({saved}:{saved:(item:{ingredient_id:string;name:string;category:string;quantity:number;unit:string})=>Promise<void>}){
 const [name,setName]=useState("");
 const [category,setCategory]=useState("PANTRY");
 const [unit,setUnit]=useState("g");
 const [suggestions,setSuggestions]=useState<IngredientSuggestion[]>([]);
 const [open,setOpen]=useState(false);
 const [saving,setSaving]=useState(false);
 useEffect(()=>{const query=name.trim();if(query.length<2){setSuggestions([]);return}const timer=window.setTimeout(()=>{nutritionApi.ingredientSuggestions(query).then(setSuggestions).catch(()=>setSuggestions([]))},220);return()=>window.clearTimeout(timer)},[name]);
 const choose=(item:IngredientSuggestion)=>{setName(item.name);setCategory(item.category||"PANTRY");setUnit(item.unit||"g");setOpen(false)};
 return <form className="pantry-add" onSubmit={async event=>{event.preventDefault();if(!name.trim()||saving)return;const data=new FormData(event.currentTarget),enteredQuantity=Number(data.get("quantity")),normalizedUnit=unit==="kg"?"g":unit==="l"?"ml":unit,normalizedQuantity=enteredQuantity*(unit==="kg"||unit==="l"?1000:1);setSaving(true);try{const result=await nutritionApi.addPantry({name:name.trim(),category,quantity:enteredQuantity,unit});const added={ingredient_id:result.ingredientId,name:result.name,category,quantity:normalizedQuantity,unit:normalizedUnit};setName("");setCategory("PANTRY");setUnit("g");setSuggestions([]);await saved(added)}finally{setSaving(false)}}}>
  <div className="pantry-add-heading"><b>Añadir algo que ya tienes</b><small>Busca una variante existente o escribe tu nombre exacto.</small></div>
  <div className="pantry-name-picker"><input required value={name} onFocus={()=>setOpen(true)} onChange={event=>{setName(event.target.value);setOpen(true)}} placeholder="Ej. Pollo o hummus envasado" autoComplete="off" aria-label="Nombre del alimento"/>
   {open&&name.trim().length>=2&&<div className="pantry-suggestions">
    {suggestions.map(item=><button type="button" key={`${item.name}-${item.unit}`} onClick={()=>choose(item)}><span><b>{item.name}</b><small>{item.source==="CATALOG"?"Ya existe en tu catálogo":"Variante sugerida"}</small></span><em>{item.unit}</em></button>)}
    <button type="button" className="pantry-custom" onClick={()=>setOpen(false)}><span><b>Usar “{name.trim()}”</b><small>Guardar como nombre personalizado</small></span></button>
   </div>}
  </div>
  <select value={category} onChange={event=>setCategory(event.target.value)} aria-label="Categoría"><option value="PANTRY">Despensa</option><option value="FRUIT_VEGETABLES">Fruta y verdura</option><option value="MEAT_FISH">Carnes y pescados</option><option value="EGGS">Huevos</option><option value="DAIRY">Lácteos</option><option value="CEREALS_LEGUMES">Cereales y legumbres</option><option value="FROZEN">Congelados</option><option value="DRINKS">Bebidas</option><option value="OTHER">Otros</option></select>
  <input name="quantity" required type="number" min="0" step="0.001" placeholder="Cantidad"/>
  <select value={unit} onChange={event=>setUnit(event.target.value)} required aria-label="Unidad"><option value="g">Gramos (g)</option><option value="kg">Kilogramos (kg)</option><option value="ml">Mililitros (ml)</option><option value="l">Litros (L)</option><option value="ud">Unidades (ud)</option></select>
  <button className="primary" disabled={saving}>{saving?"Guardando…":"Añadir"}</button>
 </form>
}
function UnitSelect(){return <select name="unit" required aria-label="Unidad"><option value="g">Gramos (g)</option><option value="kg">Kilogramos (kg)</option><option value="ml">Mililitros (ml)</option><option value="l">Litros (L)</option><option value="ud">Unidades (ud)</option></select>}
function displayQuantity(value:number,unit:string){let amount=Number(value||0),normalized=(unit||"").toLowerCase();if(normalized==="mg"){amount/=1000;normalized="g"}if(normalized==="g"&&amount>=1000)return`${(amount/1000).toFixed(amount%1000?2:0)} kg`;if(normalized==="ml"&&amount>=1000)return`${(amount/1000).toFixed(amount%1000?2:0)} l`;return`${Number(amount.toFixed(2))} ${normalized||unit}`}
function mealTypeLabel(value:string){return({BREAKFAST:"Desayuno",MID_MORNING:"Media mañana",LUNCH:"Comida",SNACK:"Merienda",DINNER:"Cena",OTHER:"Otra comida"} as Record<string,string>)[value?.toUpperCase()]||value}
function localizeMeal(meal:TodayMeal):TodayMeal{return{...meal,meal_type:({BREAKFAST:"Desayuno",MID_MORNING:"Media mañana",LUNCH:"Comida",SNACK:"Merienda",DINNER:"Cena",OTHER:"Otra comida"} as Record<string,string>)[meal.meal_type?.toUpperCase()]||meal.meal_type}}
function calendarWeeks(){const start=new Date(),day=start.getDay()||7;start.setHours(12,0,0,0);start.setDate(start.getDate()-day+1);return Array.from({length:8},(_,index)=>{const monday=new Date(start);monday.setDate(start.getDate()+index*7);const sunday=new Date(monday);sunday.setDate(monday.getDate()+6);const thursday=new Date(monday);thursday.setDate(monday.getDate()+3);const yearStart=new Date(thursday.getFullYear(),0,1);const number=Math.ceil((((thursday.getTime()-yearStart.getTime())/86400000)+yearStart.getDay()+1)/7);const format=(date:Date)=>date.toLocaleDateString("es",{day:"numeric",month:"short"});return{number,label:`${index===0?"Esta semana · ":""}${format(monday)} – ${format(sunday)}`}})}
