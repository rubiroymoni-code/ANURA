package app.anura.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import app.anura.config.JwtService;
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

    AuthController(UserRepository users, PasswordEncoder passwords, JwtService jwt) {
        this.users = users; this.passwords = passwords; this.jwt = jwt;
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

    private AuthResponse response(User user) {
        return new AuthResponse(jwt.create(user.id), new UserView(user.id.toString(), user.email, user.displayName));
    }

    record RegisterRequest(@Email @NotBlank String email, @NotBlank @Size(min=8, max=100) String password, @NotBlank @Size(max=100) String displayName) {}
    record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    record UserView(String id, String email, String displayName) {}
    record AuthResponse(String token, UserView user) {}
}
