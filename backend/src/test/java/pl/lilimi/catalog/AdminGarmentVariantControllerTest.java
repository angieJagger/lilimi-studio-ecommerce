package pl.lilimi.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.lilimi.PostgresTestConfiguration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AdminGarmentVariantControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private GarmentVariantRepository variants;

  @Autowired
  private ProductRepository products;

  @Test
  void shouldReturnPaginatedVariants() throws Exception {
    long total = variants.findAllByProductId(
      "embroidered-002",
      PageRequest.of(0, 1)
    ).getTotalElements();

    mockMvc.perform(
        get("/api/admin/products/embroidered-002/variants")
          .with(user("admin@example.com").roles("ADMIN"))
          .param("page", "1")
          .param("size", "5")
          .accept(MediaType.APPLICATION_JSON)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page").value(1))
      .andExpect(jsonPath("$.size").value(5))
      .andExpect(jsonPath("$.items.length()").value(5))
      .andExpect(jsonPath("$.totalElements").value(
        Math.toIntExact(total)
      ))
      .andExpect(jsonPath("$.totalPages").value(
        Math.toIntExact((total + 4) / 5)
      ))
      .andExpect(jsonPath("$.items[0].productId").value(
        "embroidered-002"
      ))
      .andExpect(jsonPath("$.items[0].currency").value("PLN"));
  }

  @Test
  void shouldIncludeInactiveVariantsOfHiddenProduct() throws Exception {
    var product = products.findById("embroidered-002")
      .orElseThrow();

    product.changeVisibility(false);
    products.flush();

    var variant = variants.findAllByProductId(
      product.getId(),
      PageRequest.of(
        0,
        1,
        org.springframework.data.domain.Sort.by(
          "fit",
          "size",
          "color",
          "patternId",
          "embroideryOptionId",
          "id"
        )
      )
    ).getContent().getFirst();

    variant.changeVisibility(false);
    variants.flush();

    mockMvc.perform(
        get("/api/admin/products/embroidered-002/variants")
          .with(user("admin@example.com").roles("ADMIN"))
          .param("size", "100")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath(
        "$.items[?(@.id == '" + variant.getId() + "')].active"
      ).value(org.hamcrest.Matchers.contains(false)));
  }

  @Test
  void shouldReturnNotFoundForMissingProduct() throws Exception {
    mockMvc.perform(
        get("/api/admin/products/missing-product/variants")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectDigitalProduct() throws Exception {
    mockMvc.perform(
        get("/api/admin/products/pattern-001/variants")
          .with(user("admin@example.com").roles("ADMIN"))
      )
      .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @CsvSource({
    "-1,20",
    "0,0",
    "0,101"
  })
  void shouldRejectInvalidPagination(int page, int size)
    throws Exception {
    mockMvc.perform(
        get("/api/admin/products/embroidered-002/variants")
          .with(user("admin@example.com").roles("ADMIN"))
          .param("page", Integer.toString(page))
          .param("size", Integer.toString(size))
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRejectAnonymousUser() throws Exception {
    mockMvc.perform(
        get("/api/admin/products/embroidered-002/variants")
      )
      .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldRejectCustomer() throws Exception {
    mockMvc.perform(
        get("/api/admin/products/embroidered-002/variants")
          .with(user("customer@example.com").roles("CUSTOMER"))
      )
      .andExpect(status().isForbidden());
  }
}
