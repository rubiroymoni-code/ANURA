package app.anura.household;

import app.anura.config.CurrentUser;
import app.anura.error.ApiException;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import app.anura.notification.EmailService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/households")
public class HouseholdController {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final EmailService emails;

  HouseholdController(JdbcTemplate db, PasswordEncoder encoder, EmailService emails) {
    this.db = db;
    this.encoder = encoder;
    this.emails = emails;
  }

  @GetMapping
  List<Map<String, Object>> mine() {
    return db.queryForList(
        "SELECT h.id,h.name,m.role FROM household h JOIN household_member m ON m.household_id=h.id"
            + " WHERE m.user_id=?",
        CurrentUser.id());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, Object> create(@RequestBody Name body) {
    UUID id = UUID.randomUUID(), user = CurrentUser.id();
    db.update("INSERT INTO household(id,name,owner_id) VALUES(?,?,?)", id, body.name, user);
    db.update(
        "INSERT INTO household_member(household_id,user_id,role) VALUES(?,?,'OWNER')", id, user);
    return Map.of("id", id, "name", body.name, "role", "OWNER");
  }

  @GetMapping("/{id}/members")
  List<Map<String, Object>> members(@PathVariable UUID id) {
    member(id);
    return db.queryForList(
        "SELECT u.id,u.email,u.display_name,m.role FROM household_member m JOIN app_user u ON"
            + " u.id=m.user_id WHERE m.household_id=?",
        id);
  }

  @PostMapping("/{id}/invitations")
  Map<String, Object> invite(@PathVariable UUID id, @RequestBody Email body) {
    owner(id);
    String token = UUID.randomUUID().toString();
    UUID invitation = UUID.randomUUID();
    String email =
        body.email == null || body.email.isBlank() ? null : body.email.trim().toLowerCase();
    Instant expiresAt = Instant.now().plusSeconds(86400);
    boolean registered=email!=null&&Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM app_user WHERE lower(email)=lower(?))",Boolean.class,email));
    db.update(
        "INSERT INTO"
            + " household_invitation(id,household_id,email,token_hash,status,expires_at,invited_by)"
            + " VALUES(?,?,?,?,'PENDING',?,?)",
        invitation,
        id,
        email,
        encoder.encode(token),
        expiresAt,
        CurrentUser.id());
    String delivery="NOT_REQUESTED";
    if(email!=null){
      if(emails.enabled()){String message=registered?"Te han invitado a una unidad doméstica de ANURA. Abre la app y usa este código: ":"Te han invitado a ANURA. Crea tu cuenta con este mismo email y después usa este código para entrar en la unidad doméstica: ";try{emails.send(email,registered?"Invitación familiar en ANURA":"Te invitan a unirte a ANURA",message+token+"\n\nCaduca en 24 horas.\n"+emails.frontendUrl());delivery="SENT";}catch(RuntimeException failure){delivery="FAILED";}}else delivery="EMAIL_DISABLED";
    }
    Map<String,Object> result=new LinkedHashMap<>();result.put("id",invitation);result.put("code",token);result.put("expiresAt",expiresAt);result.put("recipientStatus",email==null?"SHAREABLE_CODE":registered?"REGISTERED_USER":"NEW_USER");result.put("deliveryStatus",delivery);return result;
  }

  @PostMapping("/invitations/accept")
  void accept(@RequestBody Code body) {
    var pending =
        db.queryForList(
            "SELECT id,household_id,token_hash,email FROM household_invitation WHERE"
                + " status='PENDING' AND expires_at>CURRENT_TIMESTAMP");
    var user = db.queryForMap("SELECT email FROM app_user WHERE id=?", CurrentUser.id());
    for (var row : pending)
      if (encoder.matches(body.code, (String) row.get("token_hash"))
          && (row.get("email") == null
              || user.get("email").toString().equalsIgnoreCase(row.get("email").toString()))) {
        db.update(
            "INSERT INTO household_member(household_id,user_id,role) VALUES(?,?,'MEMBER') ON"
                + " CONFLICT DO NOTHING",
            row.get("household_id"),
            CurrentUser.id());
        db.update(
            "UPDATE household_invitation SET status='ACCEPTED',accepted_by=? WHERE id=?",
            CurrentUser.id(),
            row.get("id"));
        return;
      }
    throw new ApiException(
        HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "Invitación inválida o caducada");
  }

  private void member(UUID id) {
    Integer n =
        db.queryForObject(
            "SELECT count(*) FROM household_member WHERE household_id=? AND user_id=?",
            Integer.class,
            id,
            CurrentUser.id());
    if (n == null || n == 0)
      throw new ApiException(HttpStatus.FORBIDDEN, "HOUSEHOLD_FORBIDDEN", "Sin acceso");
  }

  private void owner(UUID id) {
    Integer n =
        db.queryForObject(
            "SELECT count(*) FROM household WHERE id=? AND owner_id=?",
            Integer.class,
            id,
            CurrentUser.id());
    if (n == null || n == 0)
      throw new ApiException(
          HttpStatus.FORBIDDEN, "HOUSEHOLD_OWNER_REQUIRED", "Solo el propietario puede invitar");
  }

  record Name(String name) {}

  record Email(String email) {}

  record Code(String code) {}
}
