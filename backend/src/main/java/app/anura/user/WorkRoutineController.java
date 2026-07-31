package app.anura.user;

import app.anura.config.CurrentUser;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/profile/work-routine")
public class WorkRoutineController {
 private final JdbcTemplate db; WorkRoutineController(JdbcTemplate db){this.db=db;}
 @GetMapping Map<String,Object> get(){Map<String,Object> out=new LinkedHashMap<>();out.put("profile",db.queryForList("SELECT occupation,work_activity,rotating_shifts,fridge_available,microwave_available,meal_break_minutes,work_notes FROM user_work_profile WHERE user_id=?",CurrentUser.id()).stream().findFirst().orElse(Map.of()));out.put("templates",templates());out.put("calendar",calendar(LocalDate.now().minusDays(7),LocalDate.now().plusDays(60)));return out;}
 @PutMapping("/profile") void profile(@RequestBody WorkProfile r){db.update("INSERT INTO user_work_profile(user_id,occupation,work_activity,rotating_shifts,fridge_available,microwave_available,meal_break_minutes,work_notes) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET occupation=EXCLUDED.occupation,work_activity=EXCLUDED.work_activity,rotating_shifts=EXCLUDED.rotating_shifts,fridge_available=EXCLUDED.fridge_available,microwave_available=EXCLUDED.microwave_available,meal_break_minutes=EXCLUDED.meal_break_minutes,work_notes=EXCLUDED.work_notes,updated_at=CURRENT_TIMESTAMP",CurrentUser.id(),r.occupation(),r.workActivity(),r.rotatingShifts(),r.fridgeAvailable(),r.microwaveAvailable(),r.mealBreakMinutes(),r.workNotes());}
 @GetMapping("/templates") List<Map<String,Object>> templates(){return db.queryForList("SELECT id,name,work_start,work_end,training_moment,fasted_training,breakfast_location,lunch_location,snack_location,dinner_location,portable_meals,days_of_week,notes FROM daily_routine_template WHERE user_id=? ORDER BY name",CurrentUser.id());}
 @PostMapping("/templates") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> template(@RequestBody Template r){UUID id=UUID.randomUUID();db.update("INSERT INTO daily_routine_template(id,user_id,name,work_start,work_end,training_moment,fasted_training,breakfast_location,lunch_location,snack_location,dinner_location,portable_meals,days_of_week,notes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,CurrentUser.id(),r.name(),r.workStart(),r.workEnd(),r.trainingMoment(),r.fastedTraining(),r.breakfastLocation(),r.lunchLocation(),r.snackLocation(),r.dinnerLocation(),r.portableMeals(),r.daysOfWeek(),r.notes());return Map.of("id",id,"name",r.name());}
 @DeleteMapping("/templates/{id}") void deleteTemplate(@PathVariable UUID id){db.update("DELETE FROM daily_routine_template WHERE id=? AND user_id=?",id,CurrentUser.id());}
 @GetMapping("/calendar") List<Map<String,Object>> calendar(@RequestParam LocalDate from,@RequestParam LocalDate to){return db.queryForList("SELECT a.assignment_date,a.template_id,t.name,t.work_start,t.work_end,t.training_moment,t.fasted_training,t.breakfast_location,t.lunch_location,t.snack_location,t.dinner_location,t.portable_meals,a.notes FROM routine_calendar_assignment a JOIN daily_routine_template t ON t.id=a.template_id WHERE a.user_id=? AND a.assignment_date BETWEEN ? AND ? ORDER BY a.assignment_date",CurrentUser.id(),from,to);}
 @PutMapping("/calendar/{date}") void assign(@PathVariable LocalDate date,@RequestBody Assignment r){Integer owns=db.queryForObject("SELECT COUNT(*) FROM daily_routine_template WHERE id=? AND user_id=?",Integer.class,r.templateId(),CurrentUser.id());if(owns==null||owns==0)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);db.update("INSERT INTO routine_calendar_assignment(user_id,assignment_date,template_id,notes) VALUES(?,?,?,?) ON CONFLICT(user_id,assignment_date) DO UPDATE SET template_id=EXCLUDED.template_id,notes=EXCLUDED.notes",CurrentUser.id(),date,r.templateId(),r.notes());}
 @DeleteMapping("/calendar/{date}") void unassign(@PathVariable LocalDate date){db.update("DELETE FROM routine_calendar_assignment WHERE user_id=? AND assignment_date=?",CurrentUser.id(),date);}
 record WorkProfile(String occupation,String workActivity,boolean rotatingShifts,boolean fridgeAvailable,boolean microwaveAvailable,Integer mealBreakMinutes,String workNotes){}
 record Template(String name,LocalTime workStart,LocalTime workEnd,String trainingMoment,boolean fastedTraining,String breakfastLocation,String lunchLocation,String snackLocation,String dinnerLocation,String portableMeals,String daysOfWeek,String notes){}
 record Assignment(UUID templateId,String notes){}
}
