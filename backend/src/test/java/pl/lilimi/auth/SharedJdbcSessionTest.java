package pl.lilimi.auth;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pl.lilimi.PostgresTestConfiguration;
import pl.lilimi.auth.application.AccountPrincipal;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class SharedJdbcSessionTest {

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Test
  void shouldShareGuestSessionAndInvalidateItAcrossRepositories() {
    var firstNode = repository();
    var secondNode = repository();
    var session = firstNode.createSession();
    var owner = UUID.randomUUID();
    var order = UUID.randomUUID();
    String ownerKey = "pl.lilimi.payment.access.GuestOrderAccessService.submissionOwner";
    String ordersKey = "pl.lilimi.payment.access.GuestOrderAccessService.orders";

    session.setAttribute(ownerKey, owner);
    session.setAttribute(ordersKey, new ArrayList<>(List.of(order)));
    firstNode.save(session);

    try {
      var restored = secondNode.findById(session.getId());
      assertThat(restored).isNotNull();
      assertThat((UUID) restored.getAttribute(ownerKey)).isEqualTo(owner);
      assertThat((Object) restored.getAttribute(ordersKey)).isEqualTo(List.of(order));

      var anotherSession = secondNode.createSession();
      assertThat((Object) anotherSession.getAttribute(ordersKey)).isNull();
      secondNode.deleteById(session.getId());
      assertThat(firstNode.findById(session.getId())).isNull();
    } finally {
      firstNode.deleteById(session.getId());
    }
  }

  @Test
  void shouldRestoreAccountPrincipalAndRotateSessionIdAcrossRepositories() {
    var firstNode = repository();
    var secondNode = repository();
    var session = firstNode.createSession();
    var principal = new AccountPrincipal(new UserAccount(
      "shared-session@example.com", "{noop}test-only", UserRole.ADMIN
    ));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
      principal, null, principal.getAuthorities()
    ));
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    firstNode.save(session);
    String previousId = session.getId();

    try {
      var restored = secondNode.findById(previousId);
      assertThat(restored).isNotNull();
      restored.changeSessionId();
      secondNode.save(restored);
      assertThat(firstNode.findById(previousId)).isNull();

      var rotated = firstNode.findById(restored.getId());
      assertThat(rotated).isNotNull();
      var restoredContext = (org.springframework.security.core.context.SecurityContext)
        rotated.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
      var restoredPrincipal = (AccountPrincipal) restoredContext.getAuthentication().getPrincipal();
      assertThat(restoredPrincipal.getUsername()).isEqualTo(principal.getUsername());
      assertThat(restoredPrincipal.getCredentialsVersion()).isEqualTo(principal.getCredentialsVersion());
      assertThat(restoredPrincipal.getAuthorities()).isEqualTo(principal.getAuthorities());
      secondNode.deleteById(restored.getId());
    } finally {
      firstNode.deleteById(previousId);
    }
  }

  @Test
  void shouldRejectExpiredSessionOnAnotherRepository() {
    var firstNode = repository();
    var session = firstNode.createSession();
    session.setMaxInactiveInterval(Duration.ofSeconds(1));
    session.setLastAccessedTime(java.time.Instant.now().minusSeconds(5));
    firstNode.save(session);

    try {
      assertThat(repository().findById(session.getId())).isNull();
    } finally {
      firstNode.deleteById(session.getId());
    }
  }

  @SuppressWarnings("unchecked")
  private SessionRepository<Session> repository() {
    return (SessionRepository<Session>) (SessionRepository<?>)
      new JdbcIndexedSessionRepository(
        jdbc,
        new TransactionTemplate(transactionManager)
      );
  }
}
