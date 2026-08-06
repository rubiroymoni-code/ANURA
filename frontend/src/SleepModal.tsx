import { useState } from "react";
import { Trash2, X } from "lucide-react";
import { sleepApi, type SleepSession } from "./api";

const localDate = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`; };

export function SleepModal({ value, close, saved, deleted }: { value: SleepSession | null; close: () => void; saved: (value: SleepSession) => void; deleted?: () => void }) {
  const [busy, setBusy] = useState(false);
  const [quality, setQuality] = useState(value?.quality_score || 0);
  const [energy, setEnergy] = useState(value?.morning_energy || 0);
  const remove = async () => { if (!value || !deleted || !window.confirm("¿Borrar esta noche?")) return; setBusy(true); try { await sleepApi.remove(value.id); deleted(); } finally { setBusy(false); } };
  return <div className="overlay sleep-modal"><section className="modal"><button className="close-sheet" onClick={close}><X /></button><small>DESCANSO</small><h2>{value ? "Editar sueño" : "¿Cómo has dormido?"}</h2><form onSubmit={async e => { e.preventDefault(); const f = new FormData(e.currentTarget); setBusy(true); try { saved(await sleepApi.save({ sleepDate: localDate(), totalSleepMinutes: Number(f.get("minutes")), qualityScore: quality || undefined, morningEnergy: energy || undefined, bedTime: String(f.get("bed") || "") || undefined, wakeTime: String(f.get("wake") || "") || undefined, notes: String(f.get("notes") || "") || undefined })); } finally { setBusy(false); } }}><label>Minutos dormidos<input name="minutes" type="number" min="0" max="1440" required defaultValue={value?.total_sleep_minutes || 480} /></label><fieldset><legend>Calidad</legend>{[1,2,3,4,5].map(n => <button type="button" className={quality === n ? "active" : ""} key={n} onClick={() => setQuality(n)}>{n}</button>)}</fieldset><fieldset><legend>Energía</legend>{[1,2,3,4,5].map(n => <button type="button" className={energy === n ? "active" : ""} key={n} onClick={() => setEnergy(n)}>{n}</button>)}</fieldset><label>Me acosté<input name="bed" type="time" defaultValue={value?.bed_time?.slice(0,5)} /></label><label>Me levanté<input name="wake" type="time" defaultValue={value?.wake_time?.slice(0,5)} /></label><textarea name="notes" placeholder="Notas opcionales" defaultValue={value?.notes || ""} /><button className="primary" disabled={busy}>{busy ? "Guardando…" : "Guardar sueño"}</button></form>{value && deleted && <button className="danger-action" disabled={busy} onClick={remove}><Trash2 />Borrar registro</button>}</section></div>;
}
