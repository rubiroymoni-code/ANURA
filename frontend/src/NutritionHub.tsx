import { useEffect, useState } from "react";
import {
  API_BASE,
  Household,
  householdApi,
  nutritionApi,
  NutritionImportPreview,
} from "./api";
import {
  ChevronDown,
  Download,
  FileUp,
  Home,
  ShoppingBasket,
  Users,
  Utensils,
  X,
} from "lucide-react";
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
  useEffect(() => {
    void Promise.all([
      householdApi.list(),
      nutritionApi.recipes(),
      nutritionApi.plans(),
    ]).then(([h, r, p]) => {
      setHouseholds(h);
      setRecipes(r);
      setPlans(p);
    });
  }, []);
  return (
    <div className="overlay">
      <section className="modal nutrition-hub">
        <div className="modal-head">
          <div>
            <small>NUTRICIÓN COMPARTIDA</small>
            <h2>
              {section === "home"
                ? "Nutrición"
                : section === "household"
                  ? "Mi unidad doméstica"
                  : section === "import"
                    ? "Importar dieta"
                    : "Lista de compra"}
            </h2>
          </div>
          <button onClick={onClose}>
            <X />
          </button>
        </div>
        {section === "home" && (
          <>
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
            <h3>Recetas</h3>
            {recipes.slice(0, 5).map((r) => (
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
function HouseholdView({
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
                Email a invitar
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
                }}
              >
                Crear invitación
              </button>
              {generated && (
                <div className="invite-code">
                  <small>Código temporal</small>
                  <strong>{generated}</strong>
                </div>
              )}
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
              await householdApi.create(name);
              refresh();
            }}
          >
            Crear unidad doméstica
          </button>
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
  const [type, setType] = useState<"diet" | "shared-diet" | "recipes">(
    "shared-diet",
  );
  const [file, setFile] = useState<File | null>(null);
  const [p, setP] = useState<NutritionImportPreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
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
      <button
        className="primary"
        disabled={!file || busy || (!!p && !p.confirmable)}
        onClick={async () => {
          setBusy(true);
          try {
            if (!p) setP(await nutritionApi.preview(type, file!));
            else {
              await nutritionApi.confirm(p.importJobId);
              setDone(true);
            }
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
  const [mode, setMode] = useState<"mine" | "both" | "total">("both");
  const [expanded, setExpanded] = useState<number | null>(0);
  const currentUser = JSON.parse(localStorage.getItem("anura-user") || "{}");
  useEffect(() => {
    void nutritionApi.week(id).then(setRows);
  }, [id]);
  const visible =
    mode === "mine"
      ? rows.filter((r) => r.display_name === currentUser.displayName)
      : rows;
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
      <div className="import-types">
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
      </div>
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

function Shopping({ plans }: { plans: Array<{ id: string }> }) {
  const [lists, setLists] = useState<
    Array<{ id: string; week_number: number }>
  >([]);
  const [items, setItems] = useState<
    Array<{
      id: string;
      name: string;
      quantity: number;
      unit: string;
      purchased: boolean;
    }>
  >([]);
  useEffect(() => {
    void nutritionApi.shopping().then(async (l) => {
      setLists(l);
      if (l[0]) setItems(await nutritionApi.items(l[0].id));
    });
  }, []);
  return (
    <div>
      {!lists.length ? (
        <>
          <div className="empty">No hay lista generada todavía.</div>
          {plans[0] && (
            <button
              className="primary"
              onClick={async () => {
                await nutritionApi.generateShopping(plans[0].id);
                const current = await nutritionApi.shopping();
                setLists(current);
                if (current[0])
                  setItems(await nutritionApi.items(current[0].id));
              }}
            >
              Generar desde el plan
            </button>
          )}
        </>
      ) : (
        <>
          {items.map((i) => (
            <button
              className={"shopping-item " + (i.purchased ? "purchased" : "")}
              onClick={async () => {
                await nutritionApi.toggle(i.id);
                setItems(await nutritionApi.items(lists[0].id));
              }}
            >
              <span>{i.purchased ? "✓" : "○"}</span>
              <b>{i.name}</b>
              <small>
                {i.quantity} {i.unit}
              </small>
            </button>
          ))}
          <button
            className="primary secondary"
            onClick={() =>
              navigator.clipboard.writeText(
                items
                  .filter((i) => !i.purchased)
                  .map((i) => `${i.name}: ${i.quantity} ${i.unit}`)
                  .join("\n"),
              )
            }
          >
            Copiar para Google Keep
          </button>
        </>
      )}
    </div>
  );
}
