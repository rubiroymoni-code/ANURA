import type { AdherenceDashboard } from "./api";

export function AdherenceCard({data,mode}:{data:AdherenceDashboard;mode:"nutrition"|"workout"|"total"}) {
  const days=["","Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo"];
  const nutrition=mode!=="workout",workout=mode!=="nutrition";
  return <section className={`adherence-card adherence-${mode}`}>
    <div><small>ÚLTIMOS {data.days} DÍAS</small><h3>{mode==="total"?"Adherencia total":mode==="nutrition"?"Adherencia nutricional":"Adherencia al entrenamiento"}</h3><p>Incluye también lo planificado que quedó sin registrar.</p></div>
    <div className="adherence-scores">
      {nutrition&&<span><b>{Number(data.meals.score).toFixed(0)}%</b><small>Nutrición · {data.meals.expected} previstas</small></span>}
      {workout&&<span><b>{Number(data.workouts.score).toFixed(0)}%</b><small>Entreno · {data.workouts.expected} previstos</small></span>}
    </div>
    {data.weekly.length>0&&<div className="adherence-trend">{data.weekly.map(row=><span key={row.week} title={`Semana de ${row.week}`}>{nutrition&&<i style={{height:`${Number(row.meal_score||0)}%`}}/>}{workout&&<b style={{height:`${Number(row.workout_score||0)}%`}}/>}<small>{new Date(row.week+"T12:00").toLocaleDateString("es",{day:"numeric",month:"short"})}</small></span>)}</div>}
    <div className="adherence-breakdown">
      {nutrition&&<><span>{data.meals.completed} comidas completas</span><span>{data.meals.substituted} sustituidas</span><span>{data.meals.partial} parciales</span><span>{data.meals.skipped} saltadas</span>{data.meals.missing>0&&<span>{data.meals.missing} comidas sin registrar</span>}</>}
      {workout&&<><span>{data.workouts.completed} entrenos completos</span><span>{data.workouts.partial} parciales</span><span>{data.workouts.abandoned} abandonados</span>{data.workouts.missing>0&&<span>{data.workouts.missing} entrenos sin registrar</span>}</>}
    </div>
    {mode==="nutrition"&&data.patterns[0]&&<p className="adherence-pattern">Más cambios registrados: <b>{days[data.patterns[0].day_number]}</b>. Es una señal para revisar contexto, no un juicio.</p>}
    {mode==="workout"&&data.workoutReasons[0]&&<p className="adherence-pattern">Motivo más registrado: <b>{data.workoutReasons[0].reason}</b>. Úsalo para ajustar el siguiente plan.</p>}
  </section>;
}
