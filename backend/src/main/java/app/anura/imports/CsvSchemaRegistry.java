package app.anura.imports;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CsvSchemaRegistry {
    public static final String NAME = "training-plan";
    public static final String VERSION = "v1";
    public static final List<String> COLUMNS = List.of("schema_version","plan_external_id","plan_name","plan_version","user_identifier","week_number","day_number","day_name","session_name","exercise_order","exercise_code","exercise_name","muscle_group","equipment","sets","reps_min","reps_max","target_rir","target_rpe","rest_seconds","tempo","warmup_required","superset_group","alternative_exercise_code","instructions","notes","valid_from","valid_until");

    public SchemaDefinition definition() {
        return new SchemaDefinition(NAME, VERSION, "Planificación de entrenamiento", "2026-07-25", ';', COLUMNS);
    }
    public record SchemaDefinition(String name, String version, String description, String publishedAt, char separator, List<String> columns) {}
}
