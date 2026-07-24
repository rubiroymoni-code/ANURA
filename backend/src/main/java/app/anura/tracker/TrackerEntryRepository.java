package app.anura.tracker;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackerEntryRepository extends JpaRepository<TrackerEntry, UUID> {
    List<TrackerEntry> findByUserIdOrderByEntryDateDescCreatedAtDesc(UUID userId);
    List<TrackerEntry> findByUserIdAndTypeOrderByEntryDateDesc(UUID userId, String type);
    List<TrackerEntry> findByUserIdAndEntryDate(UUID userId, LocalDate date);
    Optional<TrackerEntry> findByIdAndUserId(UUID id, UUID userId);
}
