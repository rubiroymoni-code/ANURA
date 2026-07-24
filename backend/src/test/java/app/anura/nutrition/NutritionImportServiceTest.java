package app.anura.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class NutritionImportServiceTest {
  private NutritionImportService service;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null));
    service = new NutritionImportService(mock(JdbcTemplate.class), 1_048_576, 2_000);
  }

  @Test
  void sharedDietProducesPreviewWithTwoUsers() {
    String csv =
        "schema_version;plan_external_id;plan_name;plan_version;household_identifier;week_number;day_number;day_name;meal_order;meal_type;meal_name;recipe_code;recipe_name;ingredient_code;ingredient_name;category;quantity_total;unit;calories_100;protein_100;carbohydrates_100;fat_100;fiber_100;user_1_identifier;user_1_portion_multiplier;user_2_identifier;user_2_portion_multiplier;valid_from;valid_until\n"
            + "v1;menu-1;Menú"
            + " semanal;1;Casa;1;1;Lunes;1;COMIDA;Curry;CURRY;Curry;ARROZ;Arroz;CEREALES;200;g;360;7;79;1;1;jose@example.com;1.2;monica@example.com;0.8;2026-08-01;2026-08-31\n";
    var result =
        service.preview(
            "SHARED_DIET",
            new MockMultipartFile(
                "file", "diet.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
    assertThat(result.get("status")).isEqualTo("VALID");
    assertThat((java.util.List<?>) result.get("users")).hasSize(2);
  }

  @Test
  void invalidNumbersAreReportedInsteadOfPersistedAsValid() {
    String csv =
        "schema_version;plan_external_id;plan_name;plan_version;user_identifier;week_number;day_number;day_name;meal_order;meal_type;meal_name;recipe_code;recipe_name;ingredient_code;ingredient_name;category;quantity;unit;calories_100;protein_100;carbohydrates_100;fat_100;fiber_100;portion_multiplier;valid_from;valid_until\n"
            + "v1;x;Plan;uno;a@b.com;1;1;Lunes;1;COMIDA;Cena;R;R;I;I;OTROS;100;g;100;10;10;10;1;1;2026-08-01;2026-08-31\n";
    var result =
        service.preview(
            "INDIVIDUAL_DIET",
            new MockMultipartFile(
                "file", "diet.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
    assertThat(result.get("status")).isEqualTo("INVALID");
    assertThat((java.util.List<?>) result.get("issues")).isNotEmpty();
  }
}
