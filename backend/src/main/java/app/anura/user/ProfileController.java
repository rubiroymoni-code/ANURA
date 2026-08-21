package app.anura.user;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import app.anura.config.CurrentUser;
import app.anura.notification.MonthlySummaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JdbcTemplate db;
    private final MonthlySummaryService summaries;
    ProfileController(UserRepository users, PasswordEncoder passwords, JdbcTemplate db, MonthlySummaryService summaries) { this.users = users; this.passwords=passwords; this.db=db; this.summaries=summaries; }

    @GetMapping
    ProfileView get() { return view(current()); }

    @PatchMapping
    ProfileView update(@Valid @RequestBody UpdateProfile request) {
        User user = current(); user.displayName = request.displayName().trim(); user.updatedAt = Instant.now();
        return view(users.save(user));
    }

    @PatchMapping("/password")
    void password(@RequestBody ChangePassword request){User user=current();if(!passwords.matches(request.currentPassword(),user.passwordHash))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Contraseña actual incorrecta");if(request.newPassword()==null||request.newPassword().length()<8)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La contraseña debe tener al menos 8 caracteres");user.passwordHash=passwords.encode(request.newPassword());user.updatedAt=Instant.now();users.save(user);}

    @GetMapping("/preferences")
    java.util.Map<String,Object> preferences(){return db.queryForList("SELECT primary_goal,experience_level,activity_level,height_cm,training_days,limitations,biological_sex,avatar_url,reminder_email_enabled,reminder_frequency,last_summary_sent_at FROM user_preference WHERE user_id=?",CurrentUser.id()).stream().findFirst().orElse(java.util.Map.of("reminder_email_enabled",true,"reminder_frequency","MONTHLY"));}

    @PatchMapping("/preferences")
    void preferences(@RequestBody Preferences request){db.update("INSERT INTO user_preference(user_id,primary_goal,experience_level,activity_level,height_cm,training_days,limitations,biological_sex,reminder_email_enabled,reminder_frequency) VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET primary_goal=EXCLUDED.primary_goal,experience_level=EXCLUDED.experience_level,activity_level=EXCLUDED.activity_level,height_cm=EXCLUDED.height_cm,training_days=EXCLUDED.training_days,limitations=EXCLUDED.limitations,biological_sex=EXCLUDED.biological_sex,reminder_email_enabled=EXCLUDED.reminder_email_enabled,reminder_frequency=EXCLUDED.reminder_frequency,updated_at=CURRENT_TIMESTAMP",CurrentUser.id(),request.primaryGoal(),request.experienceLevel(),request.activityLevel(),request.heightCm(),request.trainingDays(),request.limitations(),request.biologicalSex(),request.reminderEmailEnabled(),"MONTHLY");}

    @GetMapping("/cycles")
    java.util.List<java.util.Map<String,Object>> cycles(){return db.queryForList("SELECT id,start_date,end_date,flow_level,symptoms,notes FROM menstrual_cycle_record WHERE user_id=? ORDER BY start_date DESC",CurrentUser.id());}

    @PostMapping("/cycles")
    java.util.Map<String,Object> cycle(@RequestBody Cycle request){if(request.startDate()==null||(request.endDate()!=null&&request.endDate().isBefore(request.startDate())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Fechas del ciclo no válidas");java.util.UUID id=java.util.UUID.randomUUID();db.update("INSERT INTO menstrual_cycle_record(id,user_id,start_date,end_date,flow_level,symptoms,notes) VALUES(?,?,?,?,?,?,?) ON CONFLICT(user_id,start_date) DO UPDATE SET end_date=EXCLUDED.end_date,flow_level=EXCLUDED.flow_level,symptoms=EXCLUDED.symptoms,notes=EXCLUDED.notes",id,CurrentUser.id(),request.startDate(),request.endDate(),request.flowLevel(),request.symptoms(),request.notes());return db.queryForMap("SELECT id,start_date,end_date,flow_level,symptoms,notes FROM menstrual_cycle_record WHERE user_id=? AND start_date=?",CurrentUser.id(),request.startDate());}

    @PutMapping("/cycles/{id}")
    java.util.Map<String,Object> updateCycle(@PathVariable java.util.UUID id,@RequestBody Cycle request){if(request.startDate()==null||(request.endDate()!=null&&request.endDate().isBefore(request.startDate())))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Fechas del ciclo no válidas");int changed=db.update("UPDATE menstrual_cycle_record SET start_date=?,end_date=?,flow_level=?,symptoms=?,notes=? WHERE id=? AND user_id=?",request.startDate(),request.endDate(),request.flowLevel(),request.symptoms(),request.notes(),id,CurrentUser.id());if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Periodo no encontrado");return db.queryForMap("SELECT id,start_date,end_date,flow_level,symptoms,notes FROM menstrual_cycle_record WHERE id=? AND user_id=?",id,CurrentUser.id());}

    @DeleteMapping("/cycles/{id}")
    void deleteCycle(@PathVariable java.util.UUID id){db.update("DELETE FROM menstrual_cycle_record WHERE id=? AND user_id=?",id,CurrentUser.id());}

    @GetMapping("/supplements")
    java.util.List<java.util.Map<String,Object>> supplements(){return db.queryForList("SELECT id,name,dose,schedule,purpose,notes,active FROM user_supplement WHERE user_id=? ORDER BY active DESC,name",CurrentUser.id());}

    @PostMapping("/supplements")
    java.util.Map<String,Object> supplement(@RequestBody Supplement request){String name=cleanSupplementName(request.name());java.util.UUID id=java.util.UUID.randomUUID();db.update("INSERT INTO user_supplement(id,user_id,name,dose,schedule,purpose,notes,active) VALUES(?,?,?,?,?,?,?,?)",id,CurrentUser.id(),name,clean(request.dose(),80),clean(request.schedule(),120),clean(request.purpose(),240),clean(request.notes(),2000),request.active()==null||request.active());return supplement(id);}

    @PatchMapping("/supplements/{id}")
    java.util.Map<String,Object> supplement(@PathVariable java.util.UUID id,@RequestBody Supplement request){String name=cleanSupplementName(request.name());int changed=db.update("UPDATE user_supplement SET name=?,dose=?,schedule=?,purpose=?,notes=?,active=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",name,clean(request.dose(),80),clean(request.schedule(),120),clean(request.purpose(),240),clean(request.notes(),2000),request.active()==null||request.active(),id,CurrentUser.id());if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Suplemento no encontrado");return supplement(id);}

    @DeleteMapping("/supplements/{id}")
    void deleteSupplement(@PathVariable java.util.UUID id){db.update("DELETE FROM user_supplement WHERE id=? AND user_id=?",id,CurrentUser.id());}

    @org.springframework.web.bind.annotation.PostMapping("/reminders/test")
    void testReminder(){summaries.send(CurrentUser.id(),false);}

    @PatchMapping("/avatar")
    void avatar(@RequestBody Avatar request){String value=request.avatarUrl();if(value==null||!value.matches("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$")||value.length()>1_200_000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Imagen no válida o demasiado grande");db.update("INSERT INTO user_preference(user_id,avatar_url) VALUES(?,?) ON CONFLICT(user_id) DO UPDATE SET avatar_url=EXCLUDED.avatar_url,updated_at=CURRENT_TIMESTAMP",CurrentUser.id(),value);}

    private User current() { return users.findById(CurrentUser.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private java.util.Map<String,Object> supplement(java.util.UUID id){return db.queryForMap("SELECT id,name,dose,schedule,purpose,notes,active FROM user_supplement WHERE id=? AND user_id=?",id,CurrentUser.id());}
    private String cleanSupplementName(String value){String cleaned=clean(value,120);if(cleaned==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Indica el nombre del suplemento");return cleaned;}
    private String clean(String value,int max){if(value==null||value.isBlank())return null;String cleaned=value.trim();return cleaned.substring(0,Math.min(max,cleaned.length()));}
    private ProfileView view(User u) { return new ProfileView(u.id.toString(), u.email, u.displayName, u.role); }
    record UpdateProfile(@NotBlank @Size(max=100) String displayName) {}
    record ChangePassword(String currentPassword,String newPassword) {}
    record Preferences(String primaryGoal,String experienceLevel,String activityLevel,java.math.BigDecimal heightCm,Integer trainingDays,String limitations,String biologicalSex,boolean reminderEmailEnabled) {}
    record Cycle(java.time.LocalDate startDate,java.time.LocalDate endDate,String flowLevel,String symptoms,String notes) {}
    record Supplement(String name,String dose,String schedule,String purpose,String notes,Boolean active) {}
    record Avatar(String avatarUrl) {}
    record ProfileView(String id, String email, String displayName, String role) {}
}
