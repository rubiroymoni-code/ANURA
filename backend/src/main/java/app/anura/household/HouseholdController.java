package app.anura.household;

import app.anura.config.CurrentUser;
import app.anura.error.ApiException;
import app.anura.notification.EmailService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/households")
public class HouseholdController {
  private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  private static final SecureRandom RANDOM = new SecureRandom();
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
    return db.queryForList("SELECT h.id,h.name,m.role FROM household h JOIN household_member m ON m.household_id=h.id WHERE m.user_id=? ORDER BY h.created_at", CurrentUser.id());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  Map<String, Object> create(@RequestBody Name body) {
    String name = cleanName(body == null ? null : body.name());
    UUID user = CurrentUser.id();
    db.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, user.toString());
    if (Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM household_member WHERE user_id=?)", Boolean.class, user)))
      throw conflict("HOUSEHOLD_ALREADY_MEMBER", "Ya perteneces a una unidad doméstica");
    UUID id = UUID.randomUUID();
    try {
      db.update("INSERT INTO household(id,name,owner_id) VALUES(?,?,?)", id, name, user);
      db.update("INSERT INTO household_member(household_id,user_id,role) VALUES(?,?,'OWNER')", id, user);
      Invitation invitation = createInvitation(id, null);
      return Map.of("household", Map.of("id", id, "name", name, "role", "OWNER"), "invitation", invitation);
    } catch (DuplicateKeyException exception) {
      throw conflict("HOUSEHOLD_ALREADY_MEMBER", "Ya perteneces a una unidad doméstica");
    }
  }

  @GetMapping("/{id}/members")
  List<Map<String, Object>> members(@PathVariable UUID id) {
    member(id);
    return db.queryForList("SELECT u.id,u.email,u.display_name,m.role FROM household_member m JOIN app_user u ON u.id=m.user_id WHERE m.household_id=? ORDER BY m.joined_at", id);
  }

  @PatchMapping("/{id}")
  @Transactional
  Map<String, Object> rename(@PathVariable UUID id, @RequestBody Name body) {
    owner(id);
    String name = cleanName(body == null ? null : body.name());
    db.update("UPDATE household SET name=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", name, id);
    return Map.of("id", id, "name", name, "role", "OWNER");
  }

  @PostMapping("/{id}/leave")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  void leave(@PathVariable UUID id) {
    member(id);
    if (Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM household WHERE id=? AND owner_id=?)", Boolean.class, id, CurrentUser.id())))
      throw conflict("OWNER_CANNOT_LEAVE", "El propietario debe eliminar la unidad doméstica");
    db.update("DELETE FROM household_member WHERE household_id=? AND user_id=?", id, CurrentUser.id());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  void delete(@PathVariable UUID id) {
    owner(id);
    db.update("DELETE FROM shopping_list WHERE household_id=?", id);
    db.update("DELETE FROM nutrition_plan WHERE household_id=?", id);
    db.update("DELETE FROM recipe WHERE household_id=?", id);
    db.update("DELETE FROM ingredient WHERE household_id=?", id);
    db.update("DELETE FROM household WHERE id=?", id);
  }

  @PostMapping("/{id}/invitations")
  @Transactional
  Map<String, Object> invite(@PathVariable UUID id, @RequestBody(required = false) Email body) {
    owner(id);
    String email = body == null || body.email() == null || body.email().isBlank() ? null : body.email().trim().toLowerCase(Locale.ROOT);
    if (email != null) {
      String ownEmail = db.queryForObject("SELECT lower(email) FROM app_user WHERE id=?", String.class, CurrentUser.id());
      if (email.equals(ownEmail)) throw conflict("CANNOT_INVITE_SELF", "No puedes invitarte a ti mismo");
    }
    db.update("UPDATE household_invitation SET status='REVOKED' WHERE household_id=? AND status='PENDING'", id);
    Invitation invitation = createInvitation(id, email);
    String delivery = "NOT_REQUESTED";
    if (email != null && emails.enabled()) {
      try {
        emails.send(email, "Invitación familiar en ANURA", "Usa este código para unirte: " + invitation.code() + "\n\nCaduca en 48 horas.\n" + emails.frontendUrl());
        delivery = "SENT";
      } catch (RuntimeException failure) { delivery = "FAILED"; }
    } else if (email != null) delivery = "EMAIL_DISABLED";
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("code", invitation.code());
    result.put("expiresAt", invitation.expiresAt());
    result.put("recipientStatus", email == null ? "SHAREABLE_CODE" : registered(email) ? "REGISTERED_USER" : "NEW_USER");
    result.put("deliveryStatus", delivery);
    return result;
  }

  @PostMapping("/invitations/accept")
  @Transactional
  Map<String, Object> accept(@RequestBody Code body) {
    String code = body == null || body.code() == null ? "" : body.code().trim().toUpperCase(Locale.ROOT);
    if (code.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVITATION_CODE_REQUIRED", "Introduce el código de invitación");
    db.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, CurrentUser.id().toString());
    if (Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM household_member WHERE user_id=?)", Boolean.class, CurrentUser.id())))
      throw conflict("HOUSEHOLD_ALREADY_MEMBER", "Ya perteneces a una unidad doméstica");
    String userEmail = db.queryForObject("SELECT lower(email) FROM app_user WHERE id=?", String.class, CurrentUser.id());
    for (Map<String, Object> row : db.queryForList("SELECT id,household_id,token_hash,email,expires_at FROM household_invitation WHERE status='PENDING'")) {
      if (!encoder.matches(code, (String) row.get("token_hash"))) continue;
      Instant expires = instant(row.get("expires_at"));
      if (!expires.isAfter(Instant.now())) throw conflict("INVITATION_EXPIRED", "El código de invitación ha caducado");
      if (row.get("email") != null && !userEmail.equalsIgnoreCase(row.get("email").toString()))
        throw new ApiException(HttpStatus.FORBIDDEN, "INVITATION_EMAIL_MISMATCH", "La invitación está reservada para otro email");
      UUID household = (UUID) row.get("household_id");
      db.update("INSERT INTO household_member(household_id,user_id,role) VALUES(?,?,'MEMBER')", household, CurrentUser.id());
      db.update("UPDATE household_invitation SET status='ACCEPTED',accepted_by=? WHERE id=? AND status='PENDING'", CurrentUser.id(), row.get("id"));
      return Map.of("householdId", household, "role", "MEMBER");
    }
    throw new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "Código inválido o ya utilizado");
  }

  private Invitation createInvitation(UUID household, String email) {
    String code = "ANURA-" + segment() + "-" + segment();
    Instant expires = Instant.now().plusSeconds(48 * 60 * 60);
    db.update("INSERT INTO household_invitation(id,household_id,email,token_hash,status,expires_at,invited_by) VALUES(?,?,?,?, 'PENDING',?,?)", UUID.randomUUID(), household, email, encoder.encode(code), expires, CurrentUser.id());
    return new Invitation(code, expires);
  }

  private static String segment() {
    StringBuilder value = new StringBuilder(4);
    for (int i = 0; i < 4; i++) value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    return value.toString();
  }

  private boolean registered(String email) {
    return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM app_user WHERE lower(email)=?)", Boolean.class, email));
  }

  private static String cleanName(String value) {
    if (value == null || value.trim().isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "HOUSEHOLD_NAME_REQUIRED", "Escribe un nombre para la unidad doméstica");
    String name = value.trim();
    if (name.length() > 160) throw new ApiException(HttpStatus.BAD_REQUEST, "HOUSEHOLD_NAME_TOO_LONG", "El nombre es demasiado largo");
    return name;
  }

  private static Instant instant(Object value) {
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
    if (value instanceof java.time.OffsetDateTime offset) return offset.toInstant();
    return Instant.parse(value.toString());
  }

  private void member(UUID id) {
    if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM household_member WHERE household_id=? AND user_id=?)", Boolean.class, id, CurrentUser.id())))
      throw new ApiException(HttpStatus.FORBIDDEN, "HOUSEHOLD_FORBIDDEN", "Sin acceso a esta unidad doméstica");
  }

  private void owner(UUID id) {
    if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM household WHERE id=? AND owner_id=?)", Boolean.class, id, CurrentUser.id())))
      throw new ApiException(HttpStatus.FORBIDDEN, "HOUSEHOLD_OWNER_REQUIRED", "Solo el propietario puede invitar");
  }

  private static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
  record Name(String name) {}
  record Email(String email) {}
  record Code(String code) {}
  record Invitation(String code, Instant expiresAt) {}
}
