package app.anura.nutrition;

import app.anura.config.CurrentUser;
import app.anura.notification.OnboardingEmailService;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class NutritionImportController {
  private final NutritionImportService service;
  private final OnboardingEmailService onboarding;

  NutritionImportController(NutritionImportService service,OnboardingEmailService onboarding) {
    this.service = service;
    this.onboarding = onboarding;
  }

  @GetMapping("/nutrition-import-schemas")
  List<Map<String, String>> schemas() {
    return List.of(
        schema("diet", "dieta_plan_v1.csv"),
        schema("shared-diet", "dieta_compartida_plan_v1.csv"),
        schema("recipes", "recetas_v1.csv"));
  }

  @GetMapping("/nutrition-import-schemas/{name}/template")
  ResponseEntity<ClassPathResource> template(@PathVariable String name) {
    String file =
        switch (name) {
          case "diet" -> "dieta_plan_v1.csv";
          case "shared-diet" -> "dieta_compartida_plan_v1.csv";
          case "recipes" -> "recetas_v1.csv";
          default ->
              throw new org.springframework.web.server.ResponseStatusException(
                  HttpStatus.NOT_FOUND);
        };
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file)
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(new ClassPathResource("contracts/" + file));
  }

  @PostMapping(
      value = "/imports/nutrition/{type}/preview",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  Map<String, Object> preview(@PathVariable String type, @RequestPart MultipartFile file) {
    return service.preview(
        switch (type) {
          case "diet" -> "INDIVIDUAL_DIET";
          case "shared-diet" -> "SHARED_DIET";
          case "recipes" -> "RECIPES";
          default ->
              throw new org.springframework.web.server.ResponseStatusException(
                  HttpStatus.NOT_FOUND);
        },
        file);
  }

  @PostMapping("/imports/nutrition/{id}/confirm")
  Map<String, Object> confirm(@PathVariable UUID id) {
    Map<String,Object> result=service.confirm(id);
    onboarding.sendIfReady(CurrentUser.id());
    return result;
  }

  private Map<String, String> schema(String name, String file) {
    return Map.of("name", name, "version", "v1", "file", file);
  }
}
