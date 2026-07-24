package app.anura.tracker;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import app.anura.config.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/entries")
public class TrackerController {
    private static final Set<String> TYPES = Set.of("WORKOUT", "MEAL", "WEIGHT", "MEASUREMENT", "GOAL");
    private final TrackerEntryRepository entries;

    TrackerController(TrackerEntryRepository entries) { this.entries = entries; }

    @GetMapping
    List<TrackerEntry> list(@RequestParam(required = false) String type) {
        UUID userId = CurrentUser.id();
        if (type == null) return entries.findByUserIdOrderByEntryDateDescCreatedAtDesc(userId);
        validateType(type);
        return entries.findByUserIdAndTypeOrderByEntryDateDesc(userId, type);
    }

    @GetMapping("/today")
    List<TrackerEntry> today() { return entries.findByUserIdAndEntryDate(CurrentUser.id(), LocalDate.now()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TrackerEntry create(@Valid @RequestBody EntryRequest request) {
        TrackerEntry entry = new TrackerEntry();
        entry.id = UUID.randomUUID();
        entry.userId = CurrentUser.id();
        entry.createdAt = Instant.now();
        apply(entry, request);
        return entries.save(entry);
    }

    @PutMapping("/{id}")
    TrackerEntry update(@PathVariable UUID id, @Valid @RequestBody EntryRequest request) {
        TrackerEntry entry = owned(id);
        apply(entry, request);
        return entries.save(entry);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) { entries.delete(owned(id)); }

    private TrackerEntry owned(UUID id) {
        return entries.findByIdAndUserId(id, CurrentUser.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void apply(TrackerEntry entry, EntryRequest request) {
        validateType(request.type());
        entry.type = request.type(); entry.title = request.title(); entry.entryDate = request.entryDate();
        entry.value = request.value(); entry.unit = request.unit(); entry.details = request.details();
        entry.notes = request.notes(); entry.completed = request.completed(); entry.updatedAt = Instant.now();
    }

    private void validateType(String type) {
        if (!TYPES.contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid entry type");
    }

    record EntryRequest(@NotBlank String type, @NotBlank @Size(max=150) String title,
        @NotNull LocalDate entryDate, BigDecimal value, @Size(max=30) String unit,
        String details, String notes, boolean completed) {}
}
