package app.anura.imports;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ImportDtos {
 private ImportDtos() {}
 public record Issue(Integer row, String column, String code, String message, String severity) {}
 public record Preview(UUID importJobId, String status, boolean confirmable, String planExternalId, String planName,
   Integer version, Integer weeks, Integer days, Integer exercises, LocalDate validFrom, LocalDate validUntil, List<Issue> issues) {}
 public record Job(UUID id, String status, String filename, String checksum, Instant createdAt, Instant expiresAt, UUID planId) {}
 public record Confirmed(UUID importJobId, UUID planId, String status) {}
}
