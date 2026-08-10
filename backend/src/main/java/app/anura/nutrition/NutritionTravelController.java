package app.anura.nutrition;

import app.anura.config.CurrentUser;
import app.anura.error.ApiException;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/nutrition/travel-modes")
public class NutritionTravelController {
  private static final Pattern DATE=Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})");
  private final JdbcTemplate db;
  NutritionTravelController(JdbcTemplate db){this.db=db;}

  @GetMapping List<Map<String,Object>> list(){
    return db.queryForList("SELECT t.id,t.title,t.start_date,t.end_date,t.status,t.general_guidance,t.exclude_from_adherence,t.exclude_from_shopping,(SELECT COUNT(*) FROM nutrition_travel_day d WHERE d.travel_mode_id=t.id) day_count FROM nutrition_travel_mode t JOIN household_member m ON m.household_id=t.household_id WHERE m.user_id=? ORDER BY t.start_date DESC",CurrentUser.id());
  }

  @GetMapping("/today") Map<String,Object> today(@RequestParam(required=false) LocalDate date){
    LocalDate selected=date==null?LocalDate.now():date;
    return db.queryForList("SELECT t.id,t.title,t.start_date,t.end_date,t.general_guidance,d.plan_label,d.guidance,d.travel_date FROM nutrition_travel_mode t JOIN household_member m ON m.household_id=t.household_id LEFT JOIN nutrition_travel_day d ON d.travel_mode_id=t.id AND d.travel_date=? WHERE m.user_id=? AND t.status='ACTIVE' AND ? BETWEEN t.start_date AND t.end_date ORDER BY t.created_at DESC LIMIT 1",selected,CurrentUser.id(),selected).stream().findFirst().orElse(Map.of());
  }

  @PostMapping @Transactional Map<String,Object> create(@RequestBody Create body){
    if(body==null||body.startDate()==null||body.endDate()==null||body.endDate().isBefore(body.startDate()))throw bad("TRAVEL_DATES_INVALID","Revisa las fechas del viaje");
    UUID household=db.query("SELECT household_id FROM household_member WHERE user_id=? ORDER BY joined_at LIMIT 1",(r,n)->r.getObject(1,UUID.class),CurrentUser.id()).stream().findFirst().orElseThrow(()->bad("HOUSEHOLD_REQUIRED","El modo viaje necesita una unidad doméstica"));
    UUID id=UUID.randomUUID();String title=clean(body.title(),"Modo viaje");
    db.update("INSERT INTO nutrition_travel_mode(id,household_id,title,start_date,end_date,created_by) VALUES(?,?,?,?,?,?)",id,household,title,body.startDate(),body.endDate(),CurrentUser.id());
    return Map.of("id",id,"prompt",prompt(id,household,title,body.startDate(),body.endDate()));
  }

  @GetMapping("/{id}/prompt") Map<String,Object> prompt(@PathVariable UUID id){
    Map<String,Object> travel=owned(id);return Map.of("prompt",prompt(id,(UUID)travel.get("household_id"),String.valueOf(travel.get("title")),asDate(travel.get("start_date")),asDate(travel.get("end_date"))));
  }

  @PostMapping("/{id}/preview") Map<String,Object> preview(@PathVariable UUID id,@RequestBody ImportBody body){
    Map<String,Object> travel=owned(id);List<Day> days=parse(body==null?null:body.content(),asDate(travel.get("start_date")),asDate(travel.get("end_date")));
    long expected=java.time.temporal.ChronoUnit.DAYS.between(asDate(travel.get("start_date")),asDate(travel.get("end_date")))+1;
    return Map.of("days",days,"confirmable",days.size()==expected,"expectedDays",expected);
  }

  @PostMapping("/{id}/import") @Transactional Map<String,Object> save(@PathVariable UUID id,@RequestBody ImportBody body){
    Map<String,Object> travel=owned(id);LocalDate start=asDate(travel.get("start_date")),end=asDate(travel.get("end_date"));List<Day> days=parse(body==null?null:body.content(),start,end);
    long expected=java.time.temporal.ChronoUnit.DAYS.between(start,end)+1;
    if(days.size()!=expected)throw bad("TRAVEL_DAYS_INCOMPLETE","Debe existir exactamente una indicación para cada día del viaje");
    db.update("DELETE FROM nutrition_travel_day WHERE travel_mode_id=?",id);
    for(Day day:days)db.update("INSERT INTO nutrition_travel_day(id,travel_mode_id,travel_date,plan_label,guidance) VALUES(?,?,?,?,?)",UUID.randomUUID(),id,day.date(),day.plan(),day.guidance());
    db.update("UPDATE nutrition_travel_mode SET status='ACTIVE',general_guidance=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",general(body.content()),id);
    return Map.of("id",id,"status","ACTIVE","days",days.size());
  }

  @DeleteMapping("/{id}") @Transactional void delete(@PathVariable UUID id){owned(id);db.update("DELETE FROM nutrition_travel_mode WHERE id=?",id);}

  private String prompt(UUID id,UUID household,String title,LocalDate start,LocalDate end){
    List<Map<String,Object>> members=db.queryForList("SELECT u.display_name,u.email,nt.calories,nt.protein,nt.carbohydrates,nt.fat FROM household_member m JOIN app_user u ON u.id=m.user_id LEFT JOIN LATERAL(SELECT calories,protein,carbohydrates,fat FROM nutrition_target WHERE user_id=u.id AND valid_from<=CURRENT_DATE ORDER BY valid_from DESC LIMIT 1)nt ON TRUE WHERE m.household_id=? ORDER BY m.joined_at",household);
    return "Prepara un modo viaje para ANURA. Viaje: "+title+". Fechas inclusivas: "+start+" a "+end+". Miembros y objetivos: "+members+". No inventes una dieta cerrada ni calorías si comeremos fuera. Da una indicación breve, práctica y no punitiva para cada fecha. Devuelve exactamente una fila por día con este formato separado por tabuladores, sin omitir fechas:\nFecha\\tDía\\tPlan\\tCriterio sencillo\nYYYY-MM-DD\\tNombre del día\\tComer fuera\\tIndicación concreta\nDespués puedes añadir notas generales fuera de la tabla. No incluyas estos días en un CSV de dieta ordinaria ni propongas compensaciones, ayunos o recortes de castigo.";
  }

  private List<Day> parse(String content,LocalDate start,LocalDate end){
    if(content==null)return List.of();Map<LocalDate,Day> found=new TreeMap<>();
    for(String raw:content.split("\\R")){Matcher matcher=DATE.matcher(raw);if(!matcher.find())continue;LocalDate date;try{date=LocalDate.parse(matcher.group(1));}catch(Exception ignored){continue;}if(date.isBefore(start)||date.isAfter(end))continue;String tail=raw.substring(matcher.end()).replaceFirst("^[\\t;|, ]+","").trim();String[] cells=tail.split("\\t|;|\\|",-1);List<String> values=Arrays.stream(cells).map(String::trim).filter(v->!v.isEmpty()).toList();String plan=values.size()>=2?values.get(values.size()-2):"Comer fuera";String guidance=values.isEmpty()?"Día flexible planificado":values.getLast();if(guidance.equalsIgnoreCase(plan)&&values.size()<2)plan="Comer fuera";found.put(date,new Day(date,plan,guidance));}
    return new ArrayList<>(found.values());
  }
  private String general(String content){if(content==null)return null;return content.lines().filter(line->!DATE.matcher(line).find()).filter(line->!line.toLowerCase().contains("fecha")&&!line.isBlank()).reduce((a,b)->a+"\n"+b).map(String::trim).orElse(null);}
  private Map<String,Object> owned(UUID id){return db.queryForList("SELECT t.* FROM nutrition_travel_mode t JOIN household_member m ON m.household_id=t.household_id WHERE t.id=? AND m.user_id=?",id,CurrentUser.id()).stream().findFirst().orElseThrow(()->bad("TRAVEL_NOT_FOUND","Modo viaje no encontrado"));}
  private LocalDate asDate(Object value){return value instanceof LocalDate d?d:((java.sql.Date)value).toLocalDate();}
  private String clean(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim().substring(0,Math.min(160,value.trim().length()));}
  private ApiException bad(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}
  record Create(String title,LocalDate startDate,LocalDate endDate){}
  record ImportBody(String content){}
  record Day(LocalDate date,String plan,String guidance){}
}
