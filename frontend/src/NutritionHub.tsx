import { useEffect, useState } from "react";
import {
  API_BASE,
  Household,
  householdApi,
  nutritionApi,
  NutritionImportPreview,
  TodayMeal,
} from "./api";
import {
  ChevronDown,
  Download,
  FileUp,
  Home,
  ShoppingBasket,
  Copy,
  MessageCircle,
  Users,
  Utensils,
  X,
} from "lucide-react";
import { HouseholdView } from "./HouseholdView";
export function NutritionHub({ onClose }: { onClose: () => void }) {
  const [section, setSection] = useState<
    "home" | "household" | "import" | "shopping" | "plan" | "recipe"
  >("home");
  const [selectedPlan, setSelectedPlan] = useState<string | null>(null);
  const [selectedRecipe, setSelectedRecipe] = useState<string | null>(null);
  const [households, setHouseholds] = useState<Household[]>([]);
  const [recipes, setRecipes] = useState<Array<{ id: string; name: string }>>(
    [],
  );
  const [plans, setPlans] = useState<
    Array<{ id: string; name: string; version: number; status: string }>
  >([]);
  const [loadError, setLoadError] = useState("");
  const [todayMeals,setTodayMeals]=useState<TodayMeal[]>([]);
  const activePlan = plans.find((plan) => plan.status === "ACTIVE") || plans[0];
  const todayRecipeNames = new Set(
    todayMeals.map((meal) => meal.recipe.trim().toLocaleLowerCase("es")),
  );
  const todayRecipes = recipes.filter((recipe) =>
    todayRecipeNames.has(recipe.name.trim().toLocaleLowerCase("es")),
  );
  useEffect(() => {
    void nutritionApi.today().then(setTodayMeals).catch(()=>setTodayMeals([]));
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
  return (
    <div className="overlay">
      <section className="modal nutrition-hub">
        <div className="modal-head">
          <div>
            <small>{households.length?"NUTRICIÓN Y HOGAR":"MI NUTRICIÓN"}</small>
            <h2>
              {section === "home"
                ? "Nutrición"
                : section === "household"
                  ? "Mi unidad doméstica"
                  : section === "import"
                    ? "Importar dieta"
                    : section === "shopping"
                      ? "Lista de compra"
                      : section === "plan"
                        ? "Plan nutricional"
                        : "Receta"}
            </h2>
          </div>
          <button onClick={onClose}>
            <X />
          </button>
        </div>
        {loadError && <div className="error" role="alert">{loadError}</div>}
        {section === "home" && (
          <>
            {activePlan && (
              <button
                className="active-nutrition-plan"
                onClick={() => {
                  setSelectedPlan(activePlan.id);
                  setSection("plan");
                }}
              >
                <span>
                  <small>PLAN ACTUAL</small>
                  <b>{activePlan.name}</b>
                  <em>Versión {activePlan.version}</em>
                </span>
                <strong>Ver mis comidas →</strong>
              </button>
            )}
            <section className="nutrition-today"><div><small>HOY</small><h3>Lo que te toca comer</h3></div>{todayMeals.length?todayMeals.map(meal=><article className={meal.status==="COMPLETED"?"completed":""} key={meal.planned_meal_id}><span><small>{meal.meal_type}</small><b>{meal.meal_name}</b><em>{Number(meal.calories||0).toFixed(0)} kcal · P {Number(meal.protein||0).toFixed(0)} · C {Number(meal.carbohydrates||0).toFixed(0)} · G {Number(meal.fat||0).toFixed(0)}</em></span><button disabled={meal.status==="COMPLETED"} onClick={async()=>{await nutritionApi.completeToday(meal.planned_meal_id);setTodayMeals(await nutritionApi.today())}}>{meal.status==="COMPLETED"?"Hecho":"Completar"}</button></article>):<p>No hay comidas asignadas para hoy en el plan activo.</p>}</section>
            <div className="nutrition-menu">
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
              <button onClick={() => setSection("shopping")}>
                <ShoppingBasket />
                <b>Lista de compra</b>
                <span>Consolidada por semana</span>
              </button>
            </div>
            <h3>Planes nutricionales</h3>
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
            <h3>Recetas de hoy</h3>
            {todayRecipes.map((r) => (
              <button
                className="nutrition-row"
                key={r.name}
                onClick={() => {
                  setSelectedRecipe(r.id);
                  setSection("recipe");
                }}
              >
                <Utensils />
                <b>{r.name}</b>
              </button>
            ))}
            {!todayRecipes.length && (
              <p>No hay recetas asignadas para hoy.</p>
            )}
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
        {section === "plan" && selectedPlan && <PlanView id={selectedPlan} />}
        {section === "recipe" && selectedRecipe && (
          <RecipeView id={selectedRecipe} />
        )}
        {section !== "home" && (
          <button className="text-btn" onClick={() => setSection("home")}>
            ← Volver a nutrición
          </button>
        )}
      </section>
    </div>
  );
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
function RecipeView({ id }: { id: string }) {
  const [rows, setRows] = useState<Array<Record<string, unknown>>>([]);
  useEffect(() => {
    void nutritionApi.recipe(id).then(setRows);
  }, [id]);
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
          <small>{calories.toFixed(0)} kcal totales calculadas</small>
        </div>
      </div>
      {rows.map((r, n) => (
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

function PlanView({ id }: { id: string }) {
  const [rows, setRows] = useState<Array<Record<string, unknown>>>([]);
  const [mode, setMode] = useState<"mine" | "both" | "total">("mine");
  const [expanded, setExpanded] = useState<number | null>(0);
  const currentUser = JSON.parse(localStorage.getItem("anura-user") || "{}");
  useEffect(() => {
    void nutritionApi.week(id).then(setRows);
  }, [id]);
  const visible =
    mode === "mine"
      ? rows.filter((r) => r.user_id === currentUser.id)
      : rows;
  const shared=new Set(rows.map(r=>String(r.user_id))).size>1;
  const grouped = Object.values(
    visible.reduce<
      Record<
        string,
        { meal: string; recipe: string; people: Array<Record<string, unknown>> }
      >
    >((acc, row) => {
      const key = `${row.day_number}-${row.meal_name}`;
      acc[key] ||= {
        meal: String(row.meal_name),
        recipe: String(row.recipe),
        people: [],
      };
      acc[key].people.push(row);
      return acc;
    }, {}),
  );
  return (
    <div>
      {shared&&<div className="import-types">
        <button
          className={mode === "mine" ? "selected" : ""}
          onClick={() => setMode("mine")}
        >
          Mis cantidades
        </button>
        <button
          className={mode === "both" ? "selected" : ""}
          onClick={() => setMode("both")}
        >
          Ambos
        </button>
        <button
          className={mode === "total" ? "selected" : ""}
          onClick={() => setMode("total")}
        >
          Total receta
        </button>
      </div>}
      {!shared&&<div className="plan-owner-label">Tu planificación y cantidades</div>}
      {!grouped.length&&<div className="empty">Este plan no contiene comidas visibles. Revisa su semana e identificador de usuario.</div>}
      {grouped.map((g, n) => (
        <article className={`meal-card ${expanded === n ? "expanded" : ""}`} key={n}>
          <button className="meal-card-head" onClick={() => setExpanded(expanded === n ? null : n)} aria-expanded={expanded === n}>
            <span className="meal-index">{String(g.people[0]?.day_number).padStart(2, "0")}</span>
            <span>
              <small>DÍA {String(g.people[0]?.day_number)}</small>
              <h3>{g.meal}</h3>
              <p>{g.recipe}</p>
            </span>
            <span className="meal-kcal">{g.people.reduce((s, p) => s + Number(p.calories || 0), 0).toFixed(0)}<small>kcal</small></span>
            <ChevronDown className="meal-chevron" />
          </button>
          <div className="meal-card-body">
            {mode === "total" ? (
              <div className="portion-total"><b>Total de la receta</b><strong>{g.people.reduce((s, p) => s + Number(p.calories || 0), 0).toFixed(0)} kcal</strong></div>
            ) : (
              g.people.map((p, i) => (
                <div className="portion" key={i}>
                  <b>{String(p.display_name)}</b>
                  <span>× {String(p.portion_multiplier)} · {Number(p.calories || 0).toFixed(0)} kcal</span>
                  <small>P {Number(p.protein || 0).toFixed(0)} · C {Number(p.carbohydrates || 0).toFixed(0)} · G {Number(p.fat || 0).toFixed(0)}</small>
                </div>
              ))
            )}
          </div>
        </article>
      ))}
      <button className="primary" onClick={() => nutritionApi.activate(id)}>
        Activar este plan
      </button>
    </div>
  );
}

function Shopping({ plans }: { plans: Array<{ id: string;name:string;status:string }> }) {
  const [lists,setLists]=useState<Awaited<ReturnType<typeof nutritionApi.shopping>>>([]),[items,setItems]=useState<Awaited<ReturnType<typeof nutritionApi.items>>>([]),[week,setWeek]=useState(1),[listId,setListId]=useState(""),[error,setError]=useState("");
  const activePlan=plans.find(p=>p.status==="ACTIVE")||plans[0];
  const load=async(preferred?:string)=>{const next=await nutritionApi.shopping();setLists(next);const selected=next.find(x=>x.id===(preferred||listId))||next.find(x=>x.week_number===week)||next[0];setListId(selected?.id||"");setItems(selected?await nutritionApi.items(selected.id):[])};
  useEffect(()=>{void load()},[]);
  const generate=async()=>{if(!activePlan)return;setError("");try{const result=await nutritionApi.generateShopping(activePlan.id,week);await load(result.id)}catch(cause){if(cause instanceof Error&&cause.message.includes("modificada")&&confirm("La lista tiene cambios manuales. ¿Regenerarla igualmente?")){const result=await nutritionApi.generateShopping(activePlan.id,week,true);await load(result.id)}else setError(cause instanceof Error?cause.message:"No se pudo generar")}};
  const refreshItems=async()=>{if(listId)setItems(await nutritionApi.items(listId))};
  const categories=[...new Set(items.map(i=>i.category||"OTHER"))];
  return <div className="shopping-view">
    <div className="shopping-toolbar"><label>Semana<select value={week} onChange={e=>setWeek(Number(e.target.value))}>{[1,2,3,4,5,6,7,8].map(n=><option key={n} value={n}>Semana {n}</option>)}</select></label><button className="primary" disabled={!activePlan} onClick={generate}>{lists.some(x=>x.week_number===week)?"Regenerar semana":"Generar desde el plan"}</button></div>
    {error&&<p className="error">{error}</p>}
    {lists.length>0&&<select className="shopping-list-select" value={listId} onChange={async e=>{setListId(e.target.value);setItems(await nutritionApi.items(e.target.value))}}>{lists.map(l=><option key={l.id} value={l.id}>Semana {l.week_number}</option>)}</select>}
    {!listId?<div className="empty">Genera la lista de la semana desde tu plan activo.</div>:<>
      <div className="pantry-note">La despensa descuenta automáticamente los sobrantes comprados en semanas anteriores.</div>
      {categories.map(category=><section className="shopping-category" key={category}><h4>{categoryLabel(category)}</h4>{items.filter(i=>(i.category||"OTHER")===category).map(i=><article className={`shopping-item ${i.purchased?"purchased":""}`} key={i.id}>
        <button className="shopping-check" onClick={async()=>{await nutritionApi.toggle(i.id);await refreshItems()}}>{i.purchased?"✓":"○"}</button><div><b>{i.name}</b><small>Necesario: {i.required_quantity} {i.unit}{i.pantry_used>0?` · Despensa: ${i.pantry_used} ${i.unit}`:""}</small></div><label>Comprar<input type="number" min="0" step="0.001" defaultValue={i.quantity} onBlur={async e=>{await nutritionApi.shoppingQuantity(i.id,Number(e.target.value));await refreshItems()}}/><span>{i.unit}</span></label>
      </article>)}</section>)}
      <form className="shopping-add" onSubmit={async e=>{e.preventDefault();const f=new FormData(e.currentTarget);await nutritionApi.addShoppingItem(listId,{name:String(f.get("name")),category:String(f.get("category")),quantity:Number(f.get("quantity")),unit:String(f.get("unit"))});e.currentTarget.reset();await refreshItems()}}><b>Añadir a la casa</b><input name="name" required placeholder="Producto"/><select name="category"><option value="OTHER">Otros</option><option value="PANTRY">Despensa</option><option value="FRUIT_VEGETABLES">Fruta y verdura</option><option value="MEAT_FISH">Carnes y pescados</option><option value="DAIRY">Lácteos</option></select><input name="quantity" required type="number" min="0" step="0.001" placeholder="Cantidad"/><input name="unit" required placeholder="ud, g, ml…"/><button className="primary">Añadir</button></form>
      <button className="primary secondary" onClick={()=>navigator.clipboard.writeText(items.filter(i=>!i.purchased&&i.quantity>0).map(i=>`${i.name}: ${i.quantity} ${i.unit}`).join("\n"))}>Copiar pendientes</button>
    </>}
  </div>;
}
function categoryLabel(value:string){return ({FRUIT_VEGETABLES:"Fruta y verdura",MEAT_FISH:"Carnes y pescados",DAIRY:"Lácteos",CEREALS_LEGUMES:"Cereales y legumbres",FROZEN:"Congelados",PANTRY:"Despensa",DRINKS:"Bebidas",OTHER:"Otros"} as Record<string,string>)[value]||value}
