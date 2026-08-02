import { useEffect, useState } from "react";
import { Edit3, Pill, Plus, Trash2, X } from "lucide-react";
import { api, type Supplement } from "./api";

export function SupplementsPanel(){
 const [rows,setRows]=useState<Supplement[]>([]),[editing,setEditing]=useState<Supplement|null|undefined>(undefined),[error,setError]=useState("");
 const load=()=>api.supplements().then(setRows).catch(cause=>setError(cause instanceof Error?cause.message:"No se pudieron cargar los suplementos"));
 useEffect(()=>{void load()},[]);
 return <section className="supplements-panel">
  <div className="supplement-intro"><Pill/><span><small>CONTEXTO NUTRICIONAL</small><h3>Suplementos</h3><p>Añade únicamente lo que tomas actualmente. ANURA lo incluirá en el prompt para evitar duplicidades y contextualizar futuras propuestas.</p></span></div>
  <p className="supplement-disclaimer">Los complementos propuestos por ChatGPT deben revisarse según tu salud, medicación y analíticas con un profesional sanitario.</p>
  <button className="supplement-add" onClick={()=>setEditing(null)}><Plus/>Añadir suplemento</button>
  {error&&<div className="error">{error}</div>}
  <div className="supplement-list">{rows.length?rows.map(row=><article className={row.active?"":"inactive"} key={row.id}><span><small>{row.active?"ACTIVO":"PAUSADO"}</small><b>{row.name}</b><p>{[row.dose,row.schedule].filter(Boolean).join(" · ")||"Sin dosis u horario"}</p>{row.purpose&&<em>{row.purpose}</em>}</span><button onClick={()=>setEditing(row)} aria-label={`Editar ${row.name}`}><Edit3/></button><button className="danger" onClick={async()=>{await api.deleteSupplement(row.id);await load()}} aria-label={`Eliminar ${row.name}`}><Trash2/></button></article>):<div className="supplement-empty"><Pill/><b>Aún no tomas suplementos registrados</b><span>Este apartado empieza vacío.</span></div>}</div>
  {editing!==undefined&&<SupplementForm value={editing} close={()=>setEditing(undefined)} saved={async()=>{setEditing(undefined);await load()}}/>}
 </section>
}

function SupplementForm({value,close,saved}:{value:Supplement|null;close:()=>void;saved:()=>void}){return <div className="supplement-form-card"><button className="supplement-close" onClick={close}><X/></button><h3>{value?"Editar suplemento":"Nuevo suplemento"}</h3><form onSubmit={async event=>{event.preventDefault();const data=new FormData(event.currentTarget),body={name:String(data.get("name")||""),dose:String(data.get("dose")||""),schedule:String(data.get("schedule")||""),purpose:String(data.get("purpose")||""),notes:String(data.get("notes")||""),active:data.get("active")==="on"};if(value)await api.updateSupplement(value.id,body);else await api.saveSupplement(body);saved()}}><label>Nombre<input required name="name" defaultValue={value?.name} placeholder="Ej. Creatina monohidrato"/></label><div><label>Dosis<input name="dose" defaultValue={value?.dose} placeholder="Ej. 5 g"/></label><label>Cuándo lo tomas<input name="schedule" defaultValue={value?.schedule} placeholder="Ej. Con la comida"/></label></div><label>Motivo<input name="purpose" defaultValue={value?.purpose} placeholder="Ej. Rendimiento y fuerza"/></label><label>Notas<textarea name="notes" defaultValue={value?.notes} placeholder="Marca, tolerancia, indicación profesional…"/></label><label className="supplement-active"><input type="checkbox" name="active" defaultChecked={value?.active??true}/>Lo estoy tomando actualmente</label><button className="primary">Guardar suplemento</button></form></div>}
