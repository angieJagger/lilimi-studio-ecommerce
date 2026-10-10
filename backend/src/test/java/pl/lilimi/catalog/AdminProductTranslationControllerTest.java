package pl.lilimi.catalog;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminProductTranslationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProductRepository products;

  @Autowired
  private ProductTranslationRepository translations;

  @Autowired
  private EntityManager entityManager;

  @Test
  void shouldSaveBothTranslationsAndIncreaseProductVersion()
    throws Exception {
    var product = products.findById("pattern-001").orElseThrow();
    long originalVersion = product.getVersion();

    mockMvc.perform(
        patch(
          "/api/admin/products/{id}/translations",
          product.getId()
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(originalVersion))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(originalVersion + 1)
      ))
      .andExpect(jsonPath("$.translations[0].language").value("en"))
      .andExpect(jsonPath("$.translations[0].name").value(
        "Updated English name"
      ))
      .andExpect(jsonPath("$.translations[1].language").value("pl"))
      .andExpect(jsonPath("$.translations[1].name").value(
        "Zmieniona polska nazwa"
      ));

    entityManager.clear();

    var polish = translation("pl");
    var english = translation("en");
    var savedProduct = products.findById("pattern-001").orElseThrow();

    assertThat(polish.getName()).isEqualTo("Zmieniona polska nazwa");
    assertThat(polish.getDescription()).isEqualTo("Nowy polski opis.");
    assertThat(english.getName()).isEqualTo("Updated English name");
    assertThat(english.getDescription()).isEqualTo(
      "Updated English description."
    );
    assertThat(savedProduct.getVersion())
      .isEqualTo(originalVersion + 1);
  }

  @Test
  void shouldRejectIncorrectVersionWithoutChangingTranslations()
    throws Exception {
    var product = products.findById("pattern-001").orElseThrow();
    long originalVersion = product.getVersion();

    var originalPolishName = translation("pl").getName();
    var originalPolishDescription = translation("pl").getDescription();
    var originalEnglishName = translation("en").getName();
    var originalEnglishDescription = translation("en").getDescription();

    mockMvc.perform(
        patch(
          "/api/admin/products/{id}/translations",
          product.getId()
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(originalVersion + 1))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(
        "PRODUCT_VERSION_CONFLICT"
      ));

    entityManager.clear();

    assertThat(translation("pl").getName())
      .isEqualTo(originalPolishName);
    assertThat(translation("pl").getDescription())
      .isEqualTo(originalPolishDescription);
    assertThat(translation("en").getName())
      .isEqualTo(originalEnglishName);
    assertThat(translation("en").getDescription())
      .isEqualTo(originalEnglishDescription);
    assertThat(
      products.findById("pattern-001").orElseThrow().getVersion()
    ).isEqualTo(originalVersion);
  }

  @Test
  void shouldReturnNotFoundForMissingProduct() throws Exception {
    mockMvc.perform(
        patch(
          "/api/admin/products/{id}/translations",
          "missing-product"
        )
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(0))
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    var product = products.findById("pattern-001").orElseThrow();

    mockMvc.perform(
        patch("/api/admin/products/{id}/translations", product.getId())
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(product.getVersion()))
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    var product = products.findById("pattern-001").orElseThrow();

    mockMvc.perform(
        patch("/api/admin/products/{id}/translations", product.getId())
          .with(user("customer@example.com").roles("CUSTOMER"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(product.getVersion()))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectRequestWithoutCsrfToken() throws Exception {
    var product = products.findById("pattern-001").orElseThrow();

    mockMvc.perform(
        patch("/api/admin/products/{id}/translations", product.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(product.getVersion()))
      )
      .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"pl", "en"})
  void shouldRejectMissingLanguageWithoutChangingTranslations(
    String missingLanguage
  ) throws Exception {
    var product = products.findById("pattern-001").orElseThrow();
    long originalVersion = product.getVersion();
    var originalPolishName = translation("pl").getName();
    var originalEnglishName = translation("en").getName();

    var remainingLanguage = missingLanguage.equals("pl") ? "en" : "pl";

    var body = """
      {
        "%s": {
          "name": "Changed name",
          "description": "Changed description"
        },
        "expectedVersion": %d
      }
      """.formatted(remainingLanguage, originalVersion);

    mockMvc.perform(
        patch("/api/admin/products/{id}/translations", product.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isBadRequest());

    entityManager.clear();

    assertThat(translation("pl").getName()).isEqualTo(originalPolishName);
    assertThat(translation("en").getName()).isEqualTo(originalEnglishName);
    assertThat(
      products.findById("pattern-001").orElseThrow().getVersion()
    ).isEqualTo(originalVersion);
  }

  @ParameterizedTest
  @ValueSource(strings = {"pl", "en"})
  void shouldRejectBlankName(String language) throws Exception {
    var product = products.findById("pattern-001").orElseThrow();

    var body = requestBody(product.getVersion()).replace(
      language.equals("pl")
        ? "  Zmieniona polska nazwa  "
        : "  Updated English name  ",
      "   "
    );

    mockMvc.perform(
        patch("/api/admin/products/{id}/translations", product.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectMissingVersion() throws Exception {
    var body = """
      {
        "pl": {
          "name": "Polska nazwa",
          "description": "Polski opis"
        },
        "en": {
          "name": "English name",
          "description": "English description"
        }
      }
      """;

    mockMvc.perform(
        patch("/api/admin/products/{id}/translations", "pattern-001")
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"pl", "en"})
  void shouldRejectBlankDescription(String language)
    throws Exception {
    var product = products.findById("pattern-001").orElseThrow();

    var body = requestBody(product.getVersion()).replace(
      language.equals("pl")
        ? "  Nowy polski opis.  "
        : "  Updated English description.  ",
      "   "
    );

    mockMvc.perform(
        patch("/api/admin/products/{id}/translations", product.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @CsvSource({
    "pl, name, 201",
    "en, name, 201",
    "pl, description, 5001",
    "en, description, 5001"
  })
  void shouldRejectContentExceedingLengthLimit(
    String language,
    String field,
    int length
  ) throws Exception {
    var product = products.findById("pattern-001").orElseThrow();
    long originalVersion = product.getVersion();
    var originalPolishName = translation("pl").getName();
    var originalEnglishName = translation("en").getName();

    var mapper = JsonMapper.builder().build();
    var body = mapper.readTree(requestBody(originalVersion));

    ((tools.jackson.databind.node.ObjectNode) body.get(language))
      .put(field, "a".repeat(length));

    mockMvc.perform(
        patch("/api/admin/products/{id}/translations", product.getId())
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(mapper.writeValueAsString(body))
      )
      .andExpect(status().isBadRequest());

    entityManager.clear();

    assertThat(translation("pl").getName()).isEqualTo(originalPolishName);
    assertThat(translation("en").getName()).isEqualTo(originalEnglishName);
    assertThat(
      products.findById("pattern-001").orElseThrow().getVersion()
    ).isEqualTo(originalVersion);
  }

  @Test
  void shouldRejectNegativeVersion() throws Exception {
    mockMvc.perform(
        patch("/api/admin/products/{id}/translations", "pattern-001")
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(requestBody(-1))
      )
      .andExpect(status().isBadRequest());
  }

  private ProductTranslation translation(String language) {
    return translations.findById(
      new ProductTranslationId("pattern-001", language)
    ).orElseThrow();
  }

  private static String requestBody(long expectedVersion) {
    return """
      {
        "pl": {
          "name": "  Zmieniona polska nazwa  ",
          "description": "  Nowy polski opis.  "
        },
        "en": {
          "name": "  Updated English name  ",
          "description": "  Updated English description.  "
        },
        "expectedVersion": %d
      }
      """.formatted(expectedVersion);
  }
}
