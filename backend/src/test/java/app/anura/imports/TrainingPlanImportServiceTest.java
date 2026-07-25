package app.anura.imports;

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

class TrainingPlanImportServiceTest {
  private TrainingPlanImportService service;

  @BeforeEach void setup(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(),null));service=new TrainingPlanImportService(mock(JdbcTemplate.class),2000,1_048_576,24);}

  @Test void acceptsExcelStyleQuotedCsvAndLegacyOnePointZeroVersion(){String header=String.join(";",CsvSchemaRegistry.COLUMNS.stream().map(c->"\""+c+"\"").toList());String row="\"1.0\";\"PLAN-1\";\"Plan demo\";\"1\";\"demo@anura.app\";\"1\";\"1\";\"Lunes\";\"Torso\";\"1\";\"PRESS\";\"Press banca\";\"Pecho\";\"Barra\";\"4\";\"6\";\"8\";\"2\";\"8\";\"120\";\"3-1-1\";\"true\";\"\";\"\";\"Control\";\"\";\"2026-07-27\";\"2026-10-18\"";var result=service.preview(new MockMultipartFile("file","plan.csv","text/csv",(header+"\n"+row).getBytes(StandardCharsets.UTF_8)));assertThat(result.confirmable()).isTrue();assertThat(result.exercises()).isEqualTo(1);}
}
