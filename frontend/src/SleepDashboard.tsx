import { useEffect, useMemo, useState } from "react";
import { Moon, Plus } from "lucide-react";
import { sleepApi, type SleepSession, type SleepSummary } from "./api";
import { SleepModal } from "./SleepModal";

export function SleepDashboard({ current, onSaved }: { current: SleepSession | null; onSaved: (value: SleepSession | null) => void }) {
  const [range, setRange] = useState<7 | 30 | 90>(30);
  const [summary, setSummary] = useState<SleepSummary | null>(null);
  const [modal, setModal] = useState(false);
  const load = () => sleepApi.summary(range).then(setSummary).catch(() => setSummary(null));
  useEffect(() => { void load(); }, [range, current]);
  const average = summary?.averageSleepMinutes || 0;
  const max = Math.max(summary?.goalMinutes || 480, ...((summary?.series || []).map(row => Number(row.total_sleep_minutes || 0))));
  const points = useMemo(() => (summary?.series || []).map((row, index, rows) => ({ x: rows.length === 1 ? 50 : 6 + index * 88 / (rows.length - 1), y: 86 - Number(row.total_sleep_minutes || 0) / max * 70 })), [summary, max]);
  const format = (minutes: number) => `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
  return <section className="sleep-dashboard"><header className="sleep-dashboard-head"><div><span className="eyebrow"><Moon size={14}/> DESCANSO</span><h2>Tu sueño</h2><p>La tendencia de tus noches, sin depender de ningún dispositivo.</p></div><div className="range-switch">{([7,30,90] as const).map(value=><button key={value} className={range===value?"active":""} onClick={()=>setRange(value)}>{value}D</button>)}</div></header>{!summary||summary.records===0?<div className="sleep-empty"><Moon/><h3>Aún no hay noches registradas</h3><p>Registra tu primera noche para empezar a ver tu tendencia.</p><button className="primary" onClick={()=>setModal(true)}><Plus/>Registrar primera noche</button></div>:<><div className="sleep-stats"><div><small>PROMEDIO</small><strong>{format(average)}</strong></div><div><small>OBJETIVO</small><strong>{average-(summary.goalMinutes||480)>=0?"+":""}{format(Math.abs(average-(summary.goalMinutes||480)))}</strong></div><div><small>CALIDAD</small><strong>{summary.averageQuality?`${summary.averageQuality.toFixed(1)}/5`:"—"}</strong></div><div><small>ENERGÍA</small><strong>{summary.averageEnergy?`${summary.averageEnergy.toFixed(1)}/5`:"—"}</strong></div><div><small>NOCHES EN OBJETIVO</small><strong>{summary.goalCompletionPercentage}%</strong></div><div><small>DEUDA</small><strong>{format(summary.sleepDebtMinutes)}</strong></div><div><small>RACHA</small><strong>{summary.currentStreak} días</strong></div></div><div className="sleep-chart"><svg viewBox="0 0 100 100" preserveAspectRatio="none"><path className="sleep-goal-line" d={`M6 ${86-(summary.goalMinutes||480)/max*70}H94`}/><polyline points={points.map(point=>`${point.x},${point.y}`).join(" ")}/></svg><small>Objetivo · 8 h</small></div><button className="secondary-action" onClick={()=>setModal(true)}><Plus/>Añadir o editar noche</button></>}{modal&&<SleepModal value={current} close={()=>setModal(false)} saved={value=>{onSaved(value);setModal(false);void load()}} deleted={()=>{onSaved(null);setModal(false);void load()}}/>}</section>;
}
