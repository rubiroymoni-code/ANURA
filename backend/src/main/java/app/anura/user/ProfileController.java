package app.anura.user;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import app.anura.config.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final UserRepository users;
    ProfileController(UserRepository users) { this.users = users; }

    @GetMapping
    ProfileView get() { return view(current()); }

    @PatchMapping
    ProfileView update(@Valid @RequestBody UpdateProfile request) {
        User user = current(); user.displayName = request.displayName().trim(); user.updatedAt = Instant.now();
        return view(users.save(user));
    }

    private User current() { return users.findById(CurrentUser.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private ProfileView view(User u) { return new ProfileView(u.id.toString(), u.email, u.displayName, u.role); }
    record UpdateProfile(@NotBlank @Size(max=100) String displayName) {}
    record ProfileView(String id, String email, String displayName, String role) {}
}
