package pl.lilimi.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
@OpenAPIDefinition(
  info = @Info(
    title = "Lilimi Studio API",
    description = "API for the product catalogue, orders, payments, and digital downloads."
  )
)
public class OpenApiConfig {
}
