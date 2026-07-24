package app.anura.tracker;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tracker_entry")
public class TrackerEntry {
    @Id public UUID id;
    public UUID userId;
    public String type;
    public String title;
    public LocalDate entryDate;
    public BigDecimal value;
    public String unit;
    public String details;
    public String notes;
    public boolean completed;
    public Instant createdAt;
    public Instant updatedAt;

    protected TrackerEntry() {}
}
