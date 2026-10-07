package pl.lilimi.auth.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.Console;
import java.util.Arrays;

@Component
@Profile("provision-admin")
public class AdminAccountProvisioningRunner implements ApplicationRunner {

  private final AdminAccountProvisioningService provisioning;
  private final ConfigurableApplicationContext context;

  public AdminAccountProvisioningRunner(
    AdminAccountProvisioningService provisioning,
    ConfigurableApplicationContext context
  ) {
    this.provisioning = provisioning;
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    Console console = System.console();

    if (console == null) {
      throw new IllegalStateException(
        "Administrator provisioning requires an interactive terminal"
      );
    }

    String email = console.readLine("Administrator email: ");
    char[] password = console.readPassword("Administrator password: ");
    char[] confirmation = console.readPassword("Confirm password: ");

    try {
      if (email == null || password == null || confirmation == null) {
        throw new IllegalStateException(
          "Administrator provisioning was cancelled"
        );
      }

      if (!Arrays.equals(password, confirmation)) {
        throw new IllegalArgumentException("Passwords do not match");
      }

      provisioning.createAdmin(
        email.trim(),
        new String(password)
      );

      console.printf("Administrator account created.%n");
    } finally {
      if (password != null) {
        Arrays.fill(password, '\0');
      }

      if (confirmation != null) {
        Arrays.fill(confirmation, '\0');
      }
    }

    context.close();
  }
}
