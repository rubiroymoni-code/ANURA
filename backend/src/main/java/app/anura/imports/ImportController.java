package app.anura.imports;

import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import app.anura.imports.ImportDtos.*;

@RestController
@RequestMapping("/api/v1")
public class ImportController {
 private final CsvSchemaRegistry schemas; private final TrainingPlanImportService imports;
 ImportController(CsvSchemaRegistry schemas,TrainingPlanImportService imports){this.schemas=schemas;this.imports=imports;}
 @GetMapping("/import-schemas") List<CsvSchemaRegistry.SchemaDefinition> schemas(){return List.of(schemas.definition());}
 @GetMapping("/import-schemas/training-plan/v1") CsvSchemaRegistry.SchemaDefinition schema(){return schemas.definition();}
 @GetMapping("/import-schemas/training-plan/v1/template") ResponseEntity<ClassPathResource> template(){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=entrenamiento_plan_v1.csv").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(new ClassPathResource("contracts/entrenamiento_plan_v1.csv"));}
 @PostMapping(value="/imports/training-plans/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) Preview preview(@RequestPart("file") MultipartFile file){return imports.preview(file);}
 @GetMapping("/imports/{id}") Job job(@PathVariable UUID id){return imports.job(id);}
 @GetMapping("/imports/{id}/errors") List<Issue> errors(@PathVariable UUID id){return imports.errors(id);}
 @PostMapping("/imports/{id}/confirm") Confirmed confirm(@PathVariable UUID id){return imports.confirm(id);}
 @DeleteMapping("/imports/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) void delete(@PathVariable UUID id){imports.delete(id);}
}
