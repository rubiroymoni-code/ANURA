import { useEffect, useMemo, useState } from "react";
import type { CSSProperties } from "react";
import { ArrowDownRight, ArrowUpRight, BatteryCharging, BedDouble, CalendarCheck2, MoonStar, Plus, Sparkles, Target } from "lucide-react";
import { sleepApi, type SleepSession, type SleepSummary } from "./api";
import { SleepModal } from "./SleepModal";

const format = (minutes: number) => `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
const dayLabel = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString("es", { weekday: "short" }).replace(".", "");

export function SleepDashboard({ current, onSaved }: { current: SleepSession | null; onSaved: (value: SleepSession | null) => void }) {
  const [range, setRange] = useState<7 | 30 | 90>(30);
  const [summary, setSummary] = useState<SleepSummary | null>(null);
  const [modal, setModal] = useState(false);
  const load = () => sleepApi.summary(range).then(setSummary).catch(() => setSummary(null));
  useEffect(() => { void load(); }, [range, current]);
  const average = summary?.averageSleepMinutes || 0;
  const goal = summary?.goalMinutes || 480;
  const difference = average - goal;
  const visibleSeries = useMemo(() => {
    const rows = summary?.series || [];
    if (range === 7) return rows.slice(-7);
    const step = Math.max(1, Math.ceil(rows.length / 14));
    return rows.filter((_, index) => index % step === 0 || index === rows.length - 1);
  }, [summary, range]);
  const max = Math.max(600, ...visibleSeries.map(row => Number(row.total_sleep_minutes || 0)));
  const recent = summary?.series?.slice(-7) || [];
  const recentAverage = recent.length ? Math.round(recent.reduce((sum, row) => sum + Number(row.total_sleep_minutes), 0) / recent.length) : average;
  const trend = recentAverage - average;

  return <section className="sleep-dashboard"><div className="sleep-command"><header className="sleep-dashboard-head"><div><span className="eyebrow"><MoonStar size={14} /> EVOLUCIÓN · DESCANSO</span><h2>Tu recuperación, noche a noche</h2><p>Duración, calidad y energía en una lectura clara para ajustar hábitos y entrenamiento.</p></div><div className="range-switch">{([7, 30, 90] as const).map(value => <button key={value} className={range === value ? "active" : ""} onClick={() => setRange(value)}>{value}D</button>)}</div></header>{!summary || summary.records === 0 ? <div className="sleep-empty"><MoonStar /><h3>Aún no hay noches registradas</h3><p>Registra tu primera noche para construir una referencia de recuperación propia.</p><button className="primary" onClick={() => setModal(true)}><Plus />Registrar primera noche</button></div> : <><div className="sleep-hero"><div className="sleep-hero-main"><span>MEDIA DEL PERIODO</span><strong>{format(average)}</strong><p className={difference >= 0 ? "positive" : "negative"}>{difference >= 0 ? <ArrowUpRight /> : <ArrowDownRight />}{format(Math.abs(difference))} {difference >= 0 ? "sobre" : "por debajo de"} tu referencia de 8 h</p></div><div className="sleep-hero-ring" style={{ "--progress": `${summary.goalCompletionPercentage * 3.6}deg` } as CSSProperties}><div><strong>{summary.goalCompletionPercentage}%</strong><small>NOCHES<br />EN OBJETIVO</small></div></div></div><div className="sleep-kpis"><article><span><BedDouble /></span><div><small>DEUDA ACUMULADA</small><strong>{format(summary.sleepDebtMinutes)}</strong><p>frente a 8 h por noche</p></div></article><article><span><Sparkles /></span><div><small>CALIDAD MEDIA</small><strong>{summary.averageQuality ? `${summary.averageQuality.toFixed(1)} / 5` : "—"}</strong><p>valoración subjetiva</p></div></article><article><span><BatteryCharging /></span><div><small>ENERGÍA MATINAL</small><strong>{summary.averageEnergy ? `${summary.averageEnergy.toFixed(1)} / 5` : "—"}</strong><p>al despertar</p></div></article><article><span><CalendarCheck2 /></span><div><small>CONSTANCIA</small><strong>{summary.currentStreak} días</strong><p>racha de registros</p></div></article></div><div className="sleep-analysis-grid"><article className="sleep-chart-card"><div className="sleep-card-head"><div><small>DURACIÓN DIARIA</small><h3>Ritmo de descanso</h3></div><span><Target /> Objetivo 8 h</span></div><div className="sleep-bars">{visibleSeries.map(row => { const minutes = Number(row.total_sleep_minutes); return <div className="sleep-bar-column" key={row.sleep_date} title={`${row.sleep_date}: ${format(minutes)}`}><div className="sleep-bar-track"><i className={minutes >= goal ? "on-target" : ""} style={{ height: `${Math.max(8, minutes / max * 100)}%` }}><em>{range === 7 ? `${Math.floor(minutes / 60)}h` : ""}</em></i></div><small>{dayLabel(row.sleep_date)}</small></div>; })}<span className="sleep-goal-marker" style={{ bottom: `calc(24px + ${(goal / max) * 176}px)` }} /></div></article><aside className="sleep-insight"><span><Sparkles /></span><small>LECTURA DEL PERIODO</small><h3>{trend >= 0 ? "La última semana mejora" : "La última semana pide atención"}</h3><p>Tu media de los últimos 7 registros es <b>{format(recentAverage)}</b>, {Math.abs(trend) < 10 ? "prácticamente estable respecto al periodo" : `${format(Math.abs(trend))} ${trend >= 0 ? "por encima" : "por debajo"} de la media general`}.</p><div><Target /><span><b>{summary.records} noches analizadas</b><small>Los datos subjetivos ayudan a interpretar la duración.</small></span></div></aside></div><button className="secondary-action sleep-add" onClick={() => setModal(true)}><Plus />Añadir o editar la noche de hoy</button></>}</div>{modal && <SleepModal value={current} close={() => setModal(false)} saved={value => { onSaved(value); setModal(false); void load(); }} deleted={() => { onSaved(null); setModal(false); void load(); }} />}</section>;
}
