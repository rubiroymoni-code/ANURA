package app.anura.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class User {
    @Id public UUID id;
    public String email;
    public String passwordHash;
    public String displayName;
    public String role;
    public boolean enabled;
    public Instant createdAt;
    public Instant updatedAt;

    protected User() {}

    public User(String email, String passwordHash, String displayName) {
        this.id = UUID.randomUUID();
        this.email = email.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.displayName = displayName.trim();
        this.role = "USER";
        this.enabled = true;
        this.createdAt = this.updatedAt = Instant.now();
    }
}
