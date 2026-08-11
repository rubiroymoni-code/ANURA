import { useEffect, useMemo, useState } from "react";
import { Apple, ChevronDown, Dumbbell, Luggage, MoonStar, Scale, Sparkles } from "lucide-react";
import { bodyProgressApi, nutritionApi, sleepApi, workoutApi, type AdherenceDashboard, type BodyCheckin, type SleepSummary, type WorkoutSummary } from "./api";

type WeeklyData={adherence:AdherenceDashboard;sleep:SleepSummary;workouts:WorkoutSummary[];checkins:BodyCheckin[];travelDays:number};
const iso=(date:Date)=>`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,"0")}-${String(date.getDate()).padStart(2,"0")}`;
const average=(values:number[])=>values.length?Math.round(values.reduce((sum,value)=>sum+value,0)/values.length):null;
const sleepLabel=(minutes:number|null)=>minutes==null?"Sin datos":`${Math.floor(minutes/60)} h ${minutes%60} min`;

export function WeeklySummary({refreshKey=""}:{refreshKey?:string}){
  const [data,setData]=useState<WeeklyData|null>(null),[open,setOpen]=useState(true),[failed,setFailed]=useState(false);
  const dates=useMemo(()=>{const now=new Date();now.setHours(0,0,0,0);const start=new Date(now);start.setDate(start.getDate()-((start.getDay()+6)%7));const previous=new Date(start);previous.setDate(previous.getDate()-7);return{today:iso(now),start:iso(start),previous:iso(previous)}},[]);
  useEffect(()=>{let active=true;setFailed(false);void Promise.all([nutritionApi.adherence(7),sleepApi.summary(14),workoutApi.history(0,50),bodyProgressApi.list(),nutritionApi.travelCalendar(dates.previous,dates.today)]).then(([adherence,sleep,workouts,checkins,travel])=>{if(active)setData({adherence,sleep,workouts,checkins,travelDays:travel.filter(day=>day.travel_date>=dates.start).length})}).catch(()=>{if(active)setFailed(true)});return()=>{active=false}},[refreshKey,dates.previous,dates.start,dates.today]);
  if(failed)return null;
  if(!data)return <section className="weekly-summary weekly-loading"><span/><span/><span/></section>;
  const currentSleep=data.sleep.series.filter(row=>row.sleep_date>=dates.start).map(row=>Number(row.total_sleep_minutes));
  const previousSleep=data.sleep.series.filter(row=>row.sleep_date>=dates.previous&&row.sleep_date<dates.start).map(row=>Number(row.total_sleep_minutes));
  const currentSleepAverage=average(currentSleep),previousSleepAverage=average(previousSleep);
  const sleepDelta=currentSleepAverage!=null&&previousSleepAverage!=null?currentSleepAverage-previousSleepAverage:null;
  const sessions=data.workouts.filter(row=>row.date>=dates.start&&row.date<=dates.today&&row.status==="COMPLETED");
  const previousSessions=data.workouts.filter(row=>row.date>=dates.previous&&row.date<dates.start&&row.status==="COMPLETED");
  const ordered=[...data.checkins].sort((a,b)=>b.checkinDate.localeCompare(a.checkinDate));
  const weightChange=ordered.length>1?Number(ordered[0].weight)-Number(ordered[1].weight):null;
  const nutritionScore=data.adherence.meals.expected?Math.round(Number(data.adherence.meals.score)):null;
  const workoutScore=data.adherence.workouts.expected?Math.round(Number(data.adherence.workouts.score)):null;
  const scores=[nutritionScore,workoutScore].filter((value):value is number=>value!=null);
  const totalScore=scores.length?Math.round(scores.reduce((sum,value)=>sum+value,0)/scores.length):null;
  const previousWeek=data.adherence.weekly.at(-2),currentWeek=data.adherence.weekly.at(-1);
  const combined=(row?:{meal_score?:number;workout_score?:number})=>{const values=[row?.meal_score,row?.workout_score].filter((value):value is number=>value!=null);return values.length?values.reduce((sum,value)=>sum+Number(value),0)/values.length:null};
  const prior=combined(previousWeek),current=combined(currentWeek),scoreDelta=prior!=null&&current!=null?Math.round(current-prior):null;
  const reading=totalScore==null?"Registra comidas o entrenamientos para construir tu primera lectura semanal.":totalScore>=85?"Semana muy consistente. Mantén el sistema que te está funcionando.":totalScore>=65?"Buen rumbo. La siguiente mejora está en cerrar lo que quedó pendiente.":"Hay margen para simplificar la semana y hacer el plan más fácil de cumplir.";
  return <section className={`weekly-summary ${open?"open":""}`}><button className="weekly-summary-head" onClick={()=>setOpen(value=>!value)} aria-expanded={open}><span className="weekly-summary-icon"><Sparkles/></span><span><small>RESUMEN SEMANAL</small><b>{totalScore==null?"Tu semana, conectada":`${totalScore}% de adherencia combinada`}</b><em>{scoreDelta==null?"Nutrición, entreno, sueño y evolución":`${scoreDelta>=0?"+":""}${scoreDelta} puntos frente a la semana anterior`}</em></span>{totalScore!=null&&<strong>{totalScore}%</strong>}<ChevronDown/></button>{open&&<div className="weekly-summary-body"><div className="weekly-metric-grid"><article><Apple/><span><small>NUTRICIÓN</small><b>{nutritionScore==null?"—":`${nutritionScore}%`}</b><em>{data.adherence.meals.completed} completas · {data.adherence.meals.missing} sin registrar</em></span></article><article><Dumbbell/><span><small>ENTRENAMIENTO</small><b>{sessions.length} sesiones</b><em>{sessions.length-previousSessions.length>=0?"+":""}{sessions.length-previousSessions.length} frente a la anterior</em></span></article><article><MoonStar/><span><small>SUEÑO MEDIO</small><b>{sleepLabel(currentSleepAverage)}</b><em>{sleepDelta==null?`${currentSleep.length} noches registradas`:`${sleepDelta>=0?"+":""}${sleepDelta} min frente a la anterior`}</em></span></article><article><Scale/><span><small>EVOLUCIÓN</small><b>{weightChange==null?"Sin comparación":`${weightChange>=0?"+":""}${weightChange.toFixed(1)} kg`}</b><em>{weightChange==null?"Necesita dos check-ins":"entre los últimos check-ins"}</em></span></article></div>{data.travelDays>0&&<div className="weekly-travel-note"><Luggage/><span><b>{data.travelDays} días en modo viaje</b><small>No cuentan como incumplimiento nutricional.</small></span></div>}<p className="weekly-reading"><Sparkles/><span><small>LECTURA DE ANURA</small><b>{reading}</b></span></p></div>}</section>;
}
