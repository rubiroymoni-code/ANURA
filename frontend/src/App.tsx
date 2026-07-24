import { useEffect,useState } from 'react';
import type { CSSProperties } from 'react';
import { Activity,Apple,BarChart3,Dumbbell,Home,LogOut,Plus,Scale,Target,X } from 'lucide-react';
import { api,Entry,EntryType,User } from './api';

const meta:Record<EntryType,{label:string;icon:typeof Activity;unit:string;color:string}>={
  WORKOUT:{label:'Entreno',icon:Dumbbell,unit:'min',color:'lime'}, MEAL:{label:'Comida',icon:Apple,unit:'kcal',color:'orange'},
  WEIGHT:{label:'Peso',icon:Scale,unit:'kg',color:'blue'}, MEASUREMENT:{label:'Medida',icon:BarChart3,unit:'cm',color:'purple'},
  GOAL:{label:'Objetivo',icon:Target,unit:'%',color:'pink'}
};

export function App(){
  const [user,setUser]=useState<User|null>(()=>{try{return JSON.parse(localStorage.getItem('anura-user')||'null')}catch{return null}});
  const [entries,setEntries]=useState<Entry[]>([]); const [tab,setTab]=useState<'HOME'|EntryType>('HOME');
  const [modal,setModal]=useState(false); const [loading,setLoading]=useState(false);
  const load=()=>{if(user) void api.entries().then(setEntries).catch(()=>logout())};
  useEffect(()=>{load()},[user]);
  function logout(){localStorage.removeItem('anura-token');localStorage.removeItem('anura-user');setUser(null)}
  if(!user)return <Auth onAuth={(u,t)=>{localStorage.setItem('anura-token',t);localStorage.setItem('anura-user',JSON.stringify(u));setUser(u)}}/>;
  const visible=tab==='HOME'?entries:entries.filter(e=>e.type===tab);
  const today=new Date().toISOString().slice(0,10); const todayItems=entries.filter(e=>e.entryDate===today);
  return <main className="shell">
    <header><div className="brand"><span>A</span> ANURA</div><button className="icon-btn" onClick={logout} aria-label="Cerrar sesión"><LogOut size={19}/></button></header>
    <section className="content">
      {tab==='HOME'?<>
        <div className="hello"><p>HOLA, {user.displayName.toUpperCase()}</p><h1>Hoy cuenta.<br/><em>Muévete.</em></h1><span>{new Intl.DateTimeFormat('es',{weekday:'long',day:'numeric',month:'long'}).format(new Date())}</span></div>
        <div className="score"><div><small>RITMO DE HOY</small><strong>{Math.min(100,todayItems.length*25)}%</strong></div><div className="ring" style={{'--score':`${Math.min(100,todayItems.length*25)*3.6}deg`} as CSSProperties}><span>{todayItems.length}</span></div></div>
        <h2>Tu día</h2><div className="quick-grid">{(['WORKOUT','MEAL','WEIGHT','GOAL'] as EntryType[]).map(type=><Quick key={type} type={type} entries={todayItems} onClick={()=>setTab(type)}/>)}</div>
        <h2>Actividad reciente</h2>
      </>:<div className="section-title"><button onClick={()=>setTab('HOME')}>← Inicio</button><p>{meta[tab].label}</p><h1>{meta[tab].label}s</h1></div>}
      <EntryList entries={visible.slice(0,12)} onDelete={async id=>{await api.remove(id);load()}}/>
    </section>
    <button className="fab" onClick={()=>setModal(true)}><Plus/></button>
    <nav>{[{id:'HOME' as const,icon:Home,label:'Inicio'},{id:'WORKOUT' as const,icon:Dumbbell,label:'Entreno'},{id:'MEAL' as const,icon:Apple,label:'Nutrición'},{id:'WEIGHT' as const,icon:Activity,label:'Evolución'}].map(n=><button className={tab===n.id?'active':''} onClick={()=>setTab(n.id)} key={n.id}><n.icon/><span>{n.label}</span></button>)}</nav>
    {modal&&<EntryModal busy={loading} onClose={()=>setModal(false)} onSave={async e=>{setLoading(true);try{await api.create(e);setModal(false);load()}finally{setLoading(false)}}}/>} 
  </main>
}

function Quick({type,entries,onClick}:{type:EntryType;entries:Entry[];onClick:()=>void}){const m=meta[type],Icon=m.icon,item=entries.find(e=>e.type===type);return <button className={`quick ${m.color}`} onClick={onClick}><Icon/><span>{m.label}</span><strong>{item?item.value?`${item.value} ${item.unit||m.unit}`:'Hecho':'Pendiente'}</strong></button>}
function EntryList({entries,onDelete}:{entries:Entry[];onDelete:(id:string)=>void}){if(!entries.length)return <div className="empty">Nada registrado todavía.<br/>Pulsa + para empezar.</div>;return <div className="entries">{entries.map(e=>{const m=meta[e.type],Icon=m.icon;return <article key={e.id}><div className={`entry-icon ${m.color}`}><Icon/></div><div><small>{m.label} · {new Date(`${e.entryDate}T12:00`).toLocaleDateString('es')}</small><h3>{e.title}</h3><p>{e.details||e.notes||'Registro completado'}</p></div>{e.value!=null&&<strong>{e.value}<small>{e.unit||m.unit}</small></strong>}<button onClick={()=>onDelete(e.id)} aria-label="Eliminar"><X/></button></article>})}</div>}

function EntryModal({onClose,onSave,busy}:{onClose:()=>void;onSave:(e:Omit<Entry,'id'>)=>void;busy:boolean}){const [type,setType]=useState<EntryType>('WORKOUT');const m=meta[type];return <div className="overlay"><form className="modal" onSubmit={e=>{e.preventDefault();const f=new FormData(e.currentTarget);onSave({type,title:String(f.get('title')),entryDate:String(f.get('date')),value:f.get('value')?Number(f.get('value')):undefined,unit:String(f.get('unit')||m.unit),details:String(f.get('details')||''),notes:'',completed:true})}}><div className="modal-head"><div><small>NUEVO REGISTRO</small><h2>Añadir al día</h2></div><button type="button" onClick={onClose}><X/></button></div><div className="types">{(Object.keys(meta) as EntryType[]).map(t=>{const I=meta[t].icon;return <button type="button" className={type===t?'selected':''} onClick={()=>setType(t)} key={t}><I/><span>{meta[t].label}</span></button>})}</div><label>Título<input required name="title" placeholder={type==='WORKOUT'?'Pierna y core':type==='MEAL'?'Desayuno':'Registro'}/></label><div className="row"><label>Fecha<input required type="date" name="date" defaultValue={new Date().toISOString().slice(0,10)}/></label><label>Valor<input type="number" step="0.01" name="value" placeholder="0"/></label><label>Unidad<input name="unit" defaultValue={m.unit} key={type}/></label></div><label>Detalles<textarea name="details" placeholder="Series, macros, sensaciones..."/></label><button className="primary" disabled={busy}>{busy?'Guardando...':'Guardar registro'}</button></form></div>}

function Auth({onAuth}:{onAuth:(u:User,t:string)=>void}){const [mode,setMode]=useState<'login'|'register'>('login');const [error,setError]=useState('');const [busy,setBusy]=useState(false);return <main className="auth"><section className="auth-art"><div className="brand light"><span>A</span> ANURA</div><div><p>ENTRENA · NÚTRETE · EVOLUCIONA</p><h1>Tu cuerpo.<br/>Tu ritmo.<br/><em>Tu historia.</em></h1></div><small>Movimiento con intención.</small></section><form onSubmit={async e=>{e.preventDefault();setBusy(true);setError('');const f=new FormData(e.currentTarget);try{const r=await api.auth(mode,{email:f.get('email'),password:f.get('password'),displayName:f.get('name')});onAuth(r.user,r.token)}catch{setError('Revisa los datos e inténtalo de nuevo')}finally{setBusy(false)}}}><p>BIENVENIDO A ANURA</p><h2>{mode==='login'?'Continúa tu camino':'Empieza hoy'}</h2>{mode==='register'&&<label>Nombre<input required name="name" autoComplete="name"/></label>}<label>Email<input required name="email" type="email" autoComplete="email"/></label><label>Contraseña<input required minLength={8} name="password" type="password" autoComplete={mode==='login'?'current-password':'new-password'}/></label>{error&&<div className="error">{error}</div>}<button className="primary" disabled={busy}>{busy?'Entrando...':mode==='login'?'Entrar':'Crear cuenta'}</button><button type="button" className="text-btn" onClick={()=>setMode(mode==='login'?'register':'login')}>{mode==='login'?'¿Primera vez? Crear cuenta':'Ya tengo cuenta'}</button></form></main>}
