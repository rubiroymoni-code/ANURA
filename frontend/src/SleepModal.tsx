import { useState } from "react";
import { Trash2, X } from "lucide-react";
import { localDate, sleepApi, type SleepSession } from "./api";

export function SleepModal({ value, close, saved, deleted }: { value: SleepSession | null; close: () => void; saved: (value: SleepSession) => void; deleted?: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [quality, setQuality] = useState(value?.quality_score || 0);
  const [energy, setEnergy] = useState(value?.morning_energy || 0);
  const hours = Math.floor((value?.total_sleep_minutes || 480) / 60);
  const minutes = (value?.total_sleep_minutes || 480) % 60;
  const remove = async () => {
    if (!value || !deleted || !window.confirm("¿Borrar esta noche?")) return;
    setBusy(true); setError("");
    try { await sleepApi.remove(value.id); deleted(); } catch (reason) { setError(reason instanceof Error ? reason.message : "No se pudo borrar el registro."); } finally { setBusy(false); }
  };
  return <div className="overlay sleep-modal"><section className="modal"><button className="close-sheet" onClick={close}><X /></button><small>DESCANSO</small><h2>{value ? "Editar sueño" : "¿Cómo has dormido?"}</h2><form onSubmit={async e => { e.preventDefault(); const form = new FormData(e.currentTarget); const h = Number(form.get("hours")); const m = Number(form.get("minutes")); if (h < 0 || h > 24 || m < 0 || m > 59 || h * 60 + m > 1440) { setError("Indica una duración entre 0 y 24 horas."); return; } setBusy(true); setError(""); try { saved(await sleepApi.save({ sleepDate: localDate(), totalSleepMinutes: h * 60 + m, qualityScore: quality || undefined, morningEnergy: energy || undefined, bedTime: String(form.get("bed") || "") || undefined, wakeTime: String(form.get("wake") || "") || undefined, notes: String(form.get("notes") || "") || undefined })); } catch (reason) { setError(reason instanceof Error ? reason.message : "No se pudo guardar el registro."); } finally { setBusy(false); } }}><label>Horas<input name="hours" type="number" min="0" max="24" required defaultValue={hours} /></label><label>Minutos<input name="minutes" type="number" min="0" max="59" required defaultValue={minutes} /></label><p>Si usas un wearable, consulta sus estadísticas para completar el registro con mayor precisión.</p><fieldset><legend>Calidad</legend>{[1,2,3,4,5].map(n => <button type="button" className={quality === n ? "active" : ""} key={n} onClick={() => setQuality(n)}>{n}</button>)}</fieldset><fieldset><legend>Energía</legend>{[1,2,3,4,5].map(n => <button type="button" className={energy === n ? "active" : ""} key={n} onClick={() => setEnergy(n)}>{n}</button>)}</fieldset><label>Me acosté<input name="bed" type="time" defaultValue={value?.bed_time?.slice(0,5)} /></label><label>Me levanté<input name="wake" type="time" defaultValue={value?.wake_time?.slice(0,5)} /></label><textarea name="notes" placeholder="Notas opcionales" defaultValue={value?.notes || ""} />{error && <p role="alert" className="form-error">{error}</p>}<button className="primary" disabled={busy}>{busy ? "Guardando…" : "Guardar sueño"}</button></form>{value && deleted && <button className="danger-action" disabled={busy} onClick={remove}><Trash2 />Borrar registro</button>}</section></div>;
}
