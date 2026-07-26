package app.anura.auth;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import app.anura.config.JwtService;
import app.anura.config.CurrentUser;
import app.anura.user.User;
import app.anura.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final JdbcTemplate db;
    private static final SecureRandom RANDOM = new SecureRandom();

    AuthController(UserRepository users, PasswordEncoder passwords, JwtService jwt, JdbcTemplate db) {
        this.users = users; this.passwords = passwords; this.jwt = jwt; this.db = db;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        User user = users.save(new User(request.email(), passwords.encode(request.password()), request.displayName()));
        return response(user);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email()).filter(User -> User.enabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwords.matches(request.password(), user.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        return response(user);
    }

    @PostMapping("/recovery-code")
    RecoveryCodeResponse createRecoveryCode() {
        byte[] random = new byte[8];
        RANDOM.nextBytes(random);
        String code = HexFormat.of().withUpperCase().formatHex(random);
        Instant expiresAt = Instant.now().plusSeconds(180L * 24 * 60 * 60);
        db.update(
                "INSERT INTO password_recovery_code(user_id,code_hash,expires_at,used_at)"
                        + " VALUES(?,?,?,NULL) ON CONFLICT(user_id) DO UPDATE SET"
                        + " code_hash=EXCLUDED.code_hash,created_at=CURRENT_TIMESTAMP,"
                        + " expires_at=EXCLUDED.expires_at,used_at=NULL",
                CurrentUser.id(), passwords.encode(code), Timestamp.from(expiresAt));
        return new RecoveryCodeResponse(code, expiresAt);
    }

    @PostMapping("/password-reset")
    @Transactional
    void resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        User user = users.findByEmailIgnoreCase(request.email())
                .filter(candidate -> candidate.enabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código de recuperación inválido"));
        var rows = db.queryForList(
                "SELECT code_hash FROM password_recovery_code WHERE user_id=?"
                        + " AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP",
                user.id);
        if (rows.isEmpty()
                || !passwords.matches(request.code().replace(" ", "").toUpperCase(),
                        rows.getFirst().get("code_hash").toString())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Código de recuperación inválido");
        }
        user.passwordHash = passwords.encode(request.newPassword());
        user.updatedAt = Instant.now();
        users.save(user);
        db.update("UPDATE password_recovery_code SET used_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id);
    }

    private AuthResponse response(User user) {
        return new AuthResponse(jwt.create(user.id), new UserView(user.id.toString(), user.email, user.displayName));
    }

    record RegisterRequest(@Email @NotBlank String email, @NotBlank @Size(min=8, max=100) String password, @NotBlank @Size(max=100) String displayName) {}
    record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    record PasswordResetRequest(@Email @NotBlank String email, @NotBlank String code,
            @NotBlank @Size(min=8, max=100) String newPassword) {}
    record RecoveryCodeResponse(String code, Instant expiresAt) {}
    record UserView(String id, String email, String displayName) {}
    record AuthResponse(String token, UserView user) {}
}
