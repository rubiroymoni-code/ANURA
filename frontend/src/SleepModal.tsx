import { useMemo, useState } from "react";
import { Clock3, MoonStar, Sparkles, Trash2, X } from "lucide-react";
import { localDate, sleepApi, type SleepSession } from "./api";

const qualityOptions = [["😫", "Muy mala"], ["😕", "Mala"], ["😐", "Normal"], ["🙂", "Buena"], ["🤩", "Excelente"]] as const;
const energyOptions = [["🪫", "Agotado"], ["🥱", "Cansado"], ["😐", "Normal"], ["🙂", "Con energía"], ["⚡", "A tope"]] as const;

function durationBetween(bed: string, wake: string) {
  if (!bed || !wake) return null;
  const [bedHour, bedMinute] = bed.split(":").map(Number);
  const [wakeHour, wakeMinute] = wake.split(":").map(Number);
  let duration = wakeHour * 60 + wakeMinute - (bedHour * 60 + bedMinute);
  if (duration <= 0) duration += 24 * 60;
  return duration;
}

const formatDuration = (minutes: number) => `${Math.floor(minutes / 60)} h ${minutes % 60} min`;

export function SleepModal({ value, close, saved, deleted }: { value: SleepSession | null; close: () => void; saved: (value: SleepSession) => void; deleted?: () => void }) {
  const initialTotal = value?.total_sleep_minutes ?? 480;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [quality, setQuality] = useState(value?.quality_score || 0);
  const [energy, setEnergy] = useState(value?.morning_energy || 0);
  const [bed, setBed] = useState(value?.bed_time?.slice(0, 5) || "23:00");
  const [wake, setWake] = useState(value?.wake_time?.slice(0, 5) || "07:00");
  const [manualTotal, setManualTotal] = useState(initialTotal);
  const [manual, setManual] = useState(Boolean(value && (!value.bed_time || !value.wake_time)));
  const calculated = useMemo(() => durationBetween(bed, wake), [bed, wake]);
  const total = manual ? manualTotal : calculated ?? manualTotal;

  const remove = async () => {
    if (!value || !deleted || !window.confirm("¿Borrar esta noche?")) return;
    setBusy(true); setError("");
    try { await sleepApi.remove(value.id); deleted(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "No se pudo borrar el registro."); }
    finally { setBusy(false); }
  };
  const scale = (title: string, options: readonly (readonly string[])[], selected: number, set: (n: number) => void) => <fieldset className="sleep-scale"><legend>{title}</legend><div>{options.map(([emoji, label], index) => <button key={label} type="button" aria-label={label} className={selected === index + 1 ? "active" : ""} onClick={() => set(index + 1)}><span>{emoji}</span></button>)}</div>{selected > 0 && <small>{options[selected - 1][1]}</small>}</fieldset>;

  return <div className="overlay sleep-modal"><section className="modal sleep-modal-card"><button type="button" aria-label="Cerrar" className="close-sheet" onClick={close}><X /></button><div className="sleep-modal-heading"><span><MoonStar /></span><div><small>REGISTRO DE DESCANSO</small><h2>{value ? "Editar la noche" : "¿Cómo has dormido?"}</h2><p>Las horas calculan la duración automáticamente.</p></div></div><form onSubmit={async event => {
    event.preventDefault();
    if (total <= 0 || total > 1440) { setError("Indica una duración válida de hasta 24 horas."); return; }
    const data = new FormData(event.currentTarget); setBusy(true); setError("");
    try { saved(await sleepApi.save({ sleepDate: localDate(), totalSleepMinutes: total, qualityScore: quality || undefined, morningEnergy: energy || undefined, bedTime: bed || undefined, wakeTime: wake || undefined, notes: String(data.get("notes") || "") || undefined })); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "No se pudo guardar el registro."); }
    finally { setBusy(false); }
  }}><div className="sleep-time-card"><div className="sleep-time-fields"><label>Me acosté<input type="time" value={bed} onChange={event => { setBed(event.target.value); setManual(false); }} /></label><span><MoonStar /></span><label>Me desperté<input type="time" value={wake} onChange={event => { setWake(event.target.value); setManual(false); }} /></label></div><div className="sleep-duration-result"><Clock3 /><span><small>DURACIÓN {manual ? "AJUSTADA" : "CALCULADA"}</small><strong>{formatDuration(total)}</strong></span><button type="button" onClick={() => setManual(current => !current)}>{manual ? "Usar horas" : "Ajustar"}</button></div>{manual && <div className="sleep-manual-fields"><label>Horas<input type="number" min="0" max="24" value={Math.floor(manualTotal / 60)} onChange={event => setManualTotal(Number(event.target.value) * 60 + manualTotal % 60)} /></label><label>Minutos<input type="number" min="0" max="59" value={manualTotal % 60} onChange={event => setManualTotal(Math.floor(manualTotal / 60) * 60 + Number(event.target.value))} /></label></div>}<p><Sparkles /> Si te acuestas antes de medianoche y despiertas después, ANURA cuenta el cambio de día.</p></div>{scale("Calidad del sueño", qualityOptions, quality, setQuality)}{scale("Energía al despertar", energyOptions, energy, setEnergy)}<label className="sleep-notes">Notas opcionales<textarea name="notes" defaultValue={value?.notes || ""} placeholder="Despertares, estrés, cena tardía, sensaciones…" /></label>{error && <p role="alert" className="form-error">{error}</p>}<button className="primary sleep-save" disabled={busy}>{busy ? "Guardando…" : "Guardar noche"}</button></form>{value && deleted && <button type="button" className="danger-action" disabled={busy} onClick={remove}><Trash2 />Borrar registro</button>}</section></div>;
}
