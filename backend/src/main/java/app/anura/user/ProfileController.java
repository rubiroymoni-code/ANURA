package app.anura.user;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    java.util.Map<String,Object> preferences(){return db.queryForList("SELECT primary_goal,experience_level,activity_level,height_cm,training_days,limitations,avatar_url,reminder_email_enabled,reminder_frequency,last_summary_sent_at FROM user_preference WHERE user_id=?",CurrentUser.id()).stream().findFirst().orElse(java.util.Map.of("reminder_email_enabled",true,"reminder_frequency","MONTHLY"));}

    @PatchMapping("/preferences")
    void preferences(@RequestBody Preferences request){db.update("INSERT INTO user_preference(user_id,primary_goal,experience_level,activity_level,height_cm,training_days,limitations,reminder_email_enabled,reminder_frequency) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET primary_goal=EXCLUDED.primary_goal,experience_level=EXCLUDED.experience_level,activity_level=EXCLUDED.activity_level,height_cm=EXCLUDED.height_cm,training_days=EXCLUDED.training_days,limitations=EXCLUDED.limitations,reminder_email_enabled=EXCLUDED.reminder_email_enabled,reminder_frequency=EXCLUDED.reminder_frequency,updated_at=CURRENT_TIMESTAMP",CurrentUser.id(),request.primaryGoal(),request.experienceLevel(),request.activityLevel(),request.heightCm(),request.trainingDays(),request.limitations(),request.reminderEmailEnabled(),"MONTHLY");}

    @org.springframework.web.bind.annotation.PostMapping("/reminders/test")
    void testReminder(){summaries.send(CurrentUser.id(),false);}

    @PatchMapping("/avatar")
    void avatar(@RequestBody Avatar request){String value=request.avatarUrl();if(value==null||!value.matches("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$")||value.length()>1_200_000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Imagen no válida o demasiado grande");db.update("INSERT INTO user_preference(user_id,avatar_url) VALUES(?,?) ON CONFLICT(user_id) DO UPDATE SET avatar_url=EXCLUDED.avatar_url,updated_at=CURRENT_TIMESTAMP",CurrentUser.id(),value);}

    private User current() { return users.findById(CurrentUser.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private ProfileView view(User u) { return new ProfileView(u.id.toString(), u.email, u.displayName, u.role); }
    record UpdateProfile(@NotBlank @Size(max=100) String displayName) {}
    record ChangePassword(String currentPassword,String newPassword) {}
    record Preferences(String primaryGoal,String experienceLevel,String activityLevel,java.math.BigDecimal heightCm,Integer trainingDays,String limitations,boolean reminderEmailEnabled) {}
    record Avatar(String avatarUrl) {}
    record ProfileView(String id, String email, String displayName, String role) {}
}
