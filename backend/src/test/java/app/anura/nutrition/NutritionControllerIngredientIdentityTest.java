package app.anura.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NutritionControllerIngredientIdentityTest {
  @Test
  void cannedTunaDescriptionsAreEquivalent() {
    assertThat(NutritionController.pantryKey("Atún envasado"))
        .isEqualTo(NutritionController.pantryKey("Atún al natural escurrido"));
  }

  @Test
  void tunaSteakIsNotEquivalentToCannedTuna() {
    assertThat(NutritionController.pantryKey("Filete de atún"))
        .isNotEqualTo(NutritionController.pantryKey("Atún envasado"));
  }

  @Test
  void accentsAndPackagingGenderDoNotCreateDuplicates() {
    assertThat(NutritionController.pantryKey("ATUN ENVASADO"))
        .isEqualTo(NutritionController.pantryKey("atún envasada"));
  }
}
