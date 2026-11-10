package pl.lilimi.catalog;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import pl.lilimi.PostgresTestConfiguration;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminProductControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProductRepository products;

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void shouldPublishDigitalProductWithZipAndPrice() throws Exception {
    var result = mockMvc.perform(
        createProduct("""
            {
              "digitalPriceInGrosz": 2490,
              "pl": {"name":"Wzór haftu Łąka","description":"Cyfrowy wzór haftu."},
              "en": {"name":"Meadow embroidery pattern","description":"A digital embroidery pattern."}
            }
          """, true)
      )
      .andExpect(status().isCreated())
      .andExpect(header().exists("Location"))
      .andExpect(jsonPath("$.slug").value("wzor-haftu-laka"))
      .andExpect(jsonPath("$.productType").value("digital"))
      .andExpect(jsonPath("$.active").value(true))
      .andReturn();

    mockMvc.perform(
        get(result.getResponse().getHeader("Location"))
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.slug").value("wzor-haftu-laka"))
      .andExpect(jsonPath("$.productCode").value(matchesPattern("pattern-[0-9]{3,}")))
      .andExpect(jsonPath("$.translations.length()").value(2))
      .andExpect(jsonPath("$.digitalPriceInGrosz").value(2490))
      .andExpect(jsonPath("$.active").value(true));

    mockMvc.perform(get("/api/products/wzor-haftu-laka"))
      .andExpect(status().isOk());
  }

  @Test
  void shouldRejectDuplicateProductSlug() throws Exception {
    mockMvc.perform(
        createProduct("""
            {
              "digitalPriceInGrosz": 2490,
              "pl": {"name":"Forest Dragon","description":"Cyfrowy wzór haftu."},
              "en": {"name":"Forest Dragon","description":"A digital embroidery pattern."}
            }
          """, true)
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("PRODUCT_SLUG_CONFLICT"));
  }

  @Test
  void shouldRejectDuplicateCustomProductCode() throws Exception {
    var body = """
      {
        "digitalPriceInGrosz": 2490,
        "pl": {"name": "%s", "description": "Cyfrowy wzór haftu."},
        "en": {"name": "Embroidery pattern", "description": "A digital embroidery pattern."},
        "productCode": "wzor-laka"
      }
      """;

    mockMvc.perform(
        createProduct(body.formatted("Łąka letnia"), true)
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.productCode").value("wzor-laka"));

    mockMvc.perform(
        createProduct(body.formatted("Łąka jesienna"), true)
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("PRODUCT_CODE_CONFLICT"));
  }

  @Test
  void shouldAddOutOfStockPhysicalProductWithCustomVariantAttributes() throws Exception {
    var result = mockMvc.perform(
        createProduct("""
            {
              "productType": "physical",
              "stockQuantity": 0,
              "pl": {"name": "Breloczek testowy", "description": "Breloczek z haftem."},
              "en": {"name": "Test keyring", "description": "An embroidered keyring."},
              "physicalConfiguration": {
                "category": "Breloczki",
                "priceInGrosz": 2490,
                "shippingPackage": {"lengthCm": 12.5, "weightKg": 0.08},
                "variantAttributes": [{"name": "Kolor", "values": ["Zielony", "Czarny"]}]
              }
            }
            """, false)
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.productType").value("physical"))
      .andExpect(jsonPath("$.productCode").value(matchesPattern("physical-[0-9]{3,}")))
      .andExpect(jsonPath("$.active").value(false))
      .andReturn();

    var location = result.getResponse().getHeader("Location");
    var version = products.findById(location.substring(location.lastIndexOf('/') + 1)).orElseThrow().getVersion();
    mockMvc.perform(get(location).with(user("admin@example.com").roles("ADMIN")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.stockQuantity").value(0))
      .andExpect(jsonPath("$.physicalConfiguration.category").value("Breloczki"))
      .andExpect(jsonPath("$.physicalConfiguration.shippingPackage.lengthCm").value(12.5))
      .andExpect(jsonPath("$.physicalConfiguration.shippingPackage.weightKg").value(0.08))
      .andExpect(jsonPath("$.physicalConfiguration.variantAttributes[0].name").value("Kolor"))
      .andExpect(jsonPath("$.physicalConfiguration.variantAttributes[0].values[1]").value("Czarny"));

    mockMvc.perform(
        patch(location + "/visibility")
          .with(user("admin@example.com").roles("ADMIN"))
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {"active": true, "expectedVersion": %d}
            """.formatted(version))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("PHYSICAL_PRODUCT_NOT_PUBLISHABLE"));
  }

  @Test
  void shouldNotSaveDigitalProductWithoutPackage() throws Exception {
    var before = products.count();
    mockMvc.perform(createProduct("""
      {
        "pl": {"name": "Bez paczki", "description": "Wzór haftu."},
        "en": {"name": "Without a package", "description": "An embroidery pattern."},
        "digitalPriceInGrosz": 2490
      }
      """, false))
      .andExpect(status().isBadRequest());

    org.assertj.core.api.Assertions.assertThat(products.count()).isEqualTo(before);
  }

  private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder createProduct(
    String body, boolean withPackage
  ) throws Exception {
    var request = multipart("/api/admin/products")
      .file(new MockMultipartFile("metadata", "", "application/json", body.getBytes(StandardCharsets.UTF_8)));
    if (withPackage) {
      var output = new ByteArrayOutputStream();
      try (var zip = new ZipOutputStream(output)) {
        zip.putNextEntry(new ZipEntry("meadow.dst"));
        zip.write(new byte[] {1, 2, 3});
        zip.closeEntry();
      }
      request.file(new MockMultipartFile("package", "meadow.zip", "application/zip", output.toByteArray()));
    }
    request.with(user("admin@example.com").roles("ADMIN")).with(csrf());
    return request;
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(
        get("/api/admin/products")
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(
        get("/api/admin/products")
          .with(user("customer@example.com").roles("CUSTOMER"))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldAllowAdministratorWithDefaultPagination()
    throws Exception {
    jdbc.update("UPDATE garment_variants SET stock_quantity = 0 WHERE product_id = 'embroidered-002'");
    jdbc.update("""
      UPDATE garment_variants SET stock_quantity = 5
      WHERE product_id = 'embroidered-002' AND pattern_id = 'pattern-001'
        AND fit = 'men' AND size = 'M' AND color = 'black' AND embroidery_option_id = 'small-front'
      """);
    jdbc.update("""
      UPDATE garment_variants SET stock_quantity = 9, active = FALSE
      WHERE product_id = 'embroidered-002' AND pattern_id = 'pattern-001'
        AND fit = 'men' AND size = 'M' AND color = 'black' AND embroidery_option_id = 'large-back'
      """);

    mockMvc.perform(
        get("/api/admin/products")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page").value(0))
      .andExpect(jsonPath("$.size").value(20))
      .andExpect(jsonPath("$.items").isArray())
      .andExpect(jsonPath("$.items[?(@.id == 'embroidered-002')].stockQuantity").value(hasItem(5)))
      .andExpect(jsonPath("$.totalElements").value(
        Math.toIntExact(products.count())
      ));
  }

  @Test
  void shouldIncludeInactiveProducts() throws Exception {
    jdbc.update(
      "UPDATE products SET active = FALSE WHERE id = ?",
      "pattern-001"
    );

    mockMvc.perform(
        get("/api/admin/products")
          .param("size", "100")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath(
        "$.items[?(@.id == 'pattern-001')].active"
      ).value(hasItem(false)))
      .andExpect(jsonPath(
        "$.items[?(@.id == 'pattern-001')].productType"
      ).value(hasItem("digital")));
  }

  @Test
  void shouldReturnRequestedPageAndSize() throws Exception {
    long total = products.count();

    mockMvc.perform(
        get("/api/admin/products")
          .param("page", "1")
          .param("size", "1")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page").value(1))
      .andExpect(jsonPath("$.size").value(1))
      .andExpect(jsonPath("$.items.length()").value(1))
      .andExpect(jsonPath("$.totalElements").value(
        Math.toIntExact(total)
      ))
      .andExpect(jsonPath("$.totalPages").value(
        Math.toIntExact(total)
      ));
  }

  @ParameterizedTest
  @CsvSource({
    "-1, 20",
    "0, 0",
    "0, 101"
  })
  void shouldRejectInvalidPagination(int page, int size)
    throws Exception {
    mockMvc.perform(
        get("/api/admin/products")
          .param("page", Integer.toString(page))
          .param("size", Integer.toString(size))
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isBadRequest());
  }
  @Test
  void shouldReturnProductDetailsWithBothTranslations()
    throws Exception {
    var product = products.findById("pattern-001").orElseThrow();

    mockMvc.perform(
        get("/api/admin/products/{id}", product.getId())
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value("pattern-001"))
      .andExpect(jsonPath("$.slug").value("forest-dragon"))
      .andExpect(jsonPath("$.productType").value("digital"))
      .andExpect(jsonPath("$.active").value(true))
      .andExpect(jsonPath("$.version").value(
        Math.toIntExact(product.getVersion())
      ))
      .andExpect(jsonPath("$.translations.length()").value(2))
      .andExpect(jsonPath("$.translations[0].language").value("en"))
      .andExpect(jsonPath("$.translations[1].language").value("pl"))
      .andExpect(jsonPath("$.translations[0].name").isNotEmpty())
      .andExpect(jsonPath("$.translations[0].description").isNotEmpty())
      .andExpect(jsonPath("$.translations[1].name").isNotEmpty())
      .andExpect(jsonPath("$.translations[1].description").isNotEmpty());
  }

  @Test
  void shouldReturnDetailsOfHiddenProduct() throws Exception {
    jdbc.update(
      "UPDATE products SET active = FALSE WHERE id = ?",
      "pattern-001"
    );

    mockMvc.perform(
        get("/api/admin/products/{id}", "pattern-001")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(false))
      .andExpect(jsonPath("$.translations.length()").value(2));
  }

  @Test
  void shouldRejectAnonymousUserForProductDetails()
    throws Exception {
    mockMvc.perform(
        get("/api/admin/products/{id}", "pattern-001")
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomerForProductDetails() throws Exception {
    mockMvc.perform(
        get("/api/admin/products/{id}", "pattern-001")
          .with(user("customer@example.com").roles("CUSTOMER"))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void shouldReturnNotFoundForMissingProductDetails()
    throws Exception {
    mockMvc.perform(
        get("/api/admin/products/{id}", "missing-product")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isNotFound());
  }
}
