import { useEffect, useState } from "react";
import { CalendarDays, Save, Trash2 } from "lucide-react";
import { api } from "./api";

type Cycles = Awaited<ReturnType<typeof api.cycles>>;

export function CycleTracker() {
  const [cycles, setCycles] = useState<Cycles>([]);
  const [message, setMessage] = useState("");

  const load = () => api.cycles().then(setCycles).catch(() => setCycles([]));
  useEffect(() => { void load(); }, []);

  return <section className="cycle-tracker">
    <div className="cycle-hero">
      <CalendarDays />
      <span><small>SEGUIMIENTO PRIVADO</small><h2>Tu ciclo</h2><p>Registra fechas y sensaciones para contextualizar tu evolución y tus entrenamientos.</p></span>
    </div>
    {message && <div className="progress-saved"><Save />{message}</div>}
    <form className="cycle-form" onSubmit={async event => {
      event.preventDefault();
      const form = event.currentTarget;
      const data = new FormData(form);
      await api.saveCycle({
        startDate: String(data.get("start")),
        endDate: String(data.get("end") || "") || undefined,
        flowLevel: String(data.get("flow") || ""),
        symptoms: String(data.get("symptoms") || ""),
        notes: String(data.get("notes") || ""),
      });
      form.reset();
      await load();
      setMessage("Periodo registrado");
    }}>
      <div className="cycle-form-row"><label>Inicio<input required name="start" type="date" /></label><label>Fin<input name="end" type="date" /></label><label>Flujo<select name="flow"><option value="">Sin indicar</option><option value="LIGHT">Ligero</option><option value="MEDIUM">Medio</option><option value="HEAVY">Abundante</option></select></label></div>
      <label>Síntomas<input name="symptoms" placeholder="Dolor, fatiga, hinchazón…" /></label>
      <label>Notas<textarea name="notes" placeholder="Sensaciones, descanso o contexto del entrenamiento…" /></label>
      <p>ANURA lo usa como contexto individual; no presupone menor rendimiento por una fase.</p>
      <button className="primary"><Save />Guardar periodo</button>
    </form>
    <h2>Historial</h2>
    <div className="cycle-list">{cycles.length ? cycles.map(cycle => <article key={cycle.id}><span><b>{new Date(cycle.start_date + "T12:00").toLocaleDateString("es")}{cycle.end_date ? ` – ${new Date(cycle.end_date + "T12:00").toLocaleDateString("es")}` : ""}</b><small>{cycle.flow_level || "Flujo sin indicar"}{cycle.symptoms ? ` · ${cycle.symptoms}` : ""}</small>{cycle.notes && <em>{cycle.notes}</em>}</span><button aria-label="Eliminar periodo" onClick={async () => { await api.deleteCycle(cycle.id); await load(); }}><Trash2 /></button></article>) : <div className="cycle-empty">Aún no hay periodos registrados.</div>}</div>
  </section>;
}
