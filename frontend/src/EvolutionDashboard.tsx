import { useMemo, useState } from "react";
import { Activity, Scale, TrendingDown, TrendingUp } from "lucide-react";
import type { Entry } from "./api";

type Range = 30 | 90 | 0;

export function EvolutionDashboard({ entries }: { entries: Entry[] }) {
  const [range, setRange] = useState<Range>(90);
  const weights = useMemo(() => {
    const sorted = entries
      .filter((entry) => entry.type === "WEIGHT" && entry.value != null)
      .sort((a, b) => a.entryDate.localeCompare(b.entryDate));
    if (!range) return sorted;
    const limit = new Date();
    limit.setDate(limit.getDate() - range);
    return sorted.filter((entry) => new Date(entry.entryDate) >= limit);
  }, [entries, range]);

  const latest = weights.at(-1);
  const first = weights[0];
  const delta = latest && first ? Number(latest.value) - Number(first.value) : 0;
  const values = weights.map((entry) => Number(entry.value));
  const min = values.length ? Math.min(...values) : 0;
  const max = values.length ? Math.max(...values) : 0;
  const span = Math.max(max - min, 1);
  const points = weights.map((entry, index) => ({
    x: weights.length === 1 ? 50 : 6 + (index / (weights.length - 1)) * 88,
    y: 82 - ((Number(entry.value) - min) / span) * 62,
    entry,
  }));
  const line = points.map((point) => `${point.x},${point.y}`).join(" ");
  const area = points.length
    ? `M ${points[0].x} 88 L ${points.map((point) => `${point.x} ${point.y}`).join(" L ")} L ${points.at(-1)!.x} 88 Z`
    : "";

  return (
    <section className="evolution-panel">
      <div className="evolution-head">
        <div>
          <span className="eyebrow"><Activity size={14} /> SEGUIMIENTO</span>
          <h2>Tu evolución</h2>
          <p>El progreso real vive en la tendencia, no en un solo día.</p>
        </div>
        <div className="range-switch" aria-label="Periodo de la gráfica">
          {([[30, "30D"], [90, "90D"], [0, "Todo"]] as const).map(([value, label]) => (
            <button key={value} className={range === value ? "active" : ""} onClick={() => setRange(value)}>{label}</button>
          ))}
        </div>
      </div>

      {weights.length ? (
        <>
          <div className="evolution-stats">
            <div><small>ACTUAL</small><strong>{Number(latest!.value).toFixed(1)} <i>{latest!.unit || "kg"}</i></strong></div>
            <div><small>CAMBIO</small><strong className={delta <= 0 ? "good" : "warm"}>{delta > 0 ? "+" : ""}{delta.toFixed(1)} <i>kg</i></strong></div>
            <div><small>REGISTROS</small><strong>{weights.length}</strong></div>
          </div>
          <div className="chart-wrap">
            <div className="chart-scale"><span>{max.toFixed(1)}</span><span>{((max + min) / 2).toFixed(1)}</span><span>{min.toFixed(1)}</span></div>
            <svg className="evolution-chart" viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="Gráfica de evolución del peso">
              <defs><linearGradient id="chartFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#b7f34a" stopOpacity=".36"/><stop offset="100%" stopColor="#b7f34a" stopOpacity="0"/></linearGradient></defs>
              <path className="chart-grid" d="M6 20H94 M6 51H94 M6 82H94" />
              <path className="chart-area" d={area} />
              <polyline className="chart-line" points={line} />
              {points.map(({ x, y, entry }) => <circle key={entry.id} className="chart-point" cx={x} cy={y} r="1.7"><title>{entry.entryDate}: {entry.value} {entry.unit || "kg"}</title></circle>)}
            </svg>
            <div className="chart-dates"><span>{first!.entryDate.slice(5)}</span><span>{latest!.entryDate.slice(5)}</span></div>
          </div>
          <div className="trend-note">
            {delta <= 0 ? <TrendingDown /> : <TrendingUp />}
            <span><b>{Math.abs(delta).toFixed(1)} kg</b> de variación en el periodo seleccionado</span>
          </div>
        </>
      ) : (
        <div className="chart-empty"><Scale /><h3>Aún no hay una tendencia</h3><p>Añade al menos un registro de peso para empezar a visualizar tu evolución.</p></div>
      )}
    </section>
  );
}
