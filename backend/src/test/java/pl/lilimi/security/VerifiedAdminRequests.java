package pl.lilimi.security;

import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.support.WebApplicationContextUtils;
import pl.lilimi.auth.application.AccountPrincipal;
import pl.lilimi.auth.domain.UserAccount;
import pl.lilimi.auth.domain.UserRole;
import pl.lilimi.auth.mfa.AdminMfaSession;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class VerifiedAdminRequests {

  private VerifiedAdminRequests() {
  }

  public static RequestPostProcessor verifiedAdmin(String email) {
    return adminRequest(email, false);
  }

  public static RequestPostProcessor forgedAdminAuthority(String email) {
    return adminRequest(email, true);
  }

  private static RequestPostProcessor adminRequest(
    String email,
    boolean forgedAuthority
  ) {
    return request -> {
      var context = WebApplicationContextUtils
        .getRequiredWebApplicationContext(request.getServletContext());

      var accounts = context.getBean(UserAccountRepository.class);

      var account = accounts.findByEmail(email)
        .orElseGet(() -> accounts.saveAndFlush(new UserAccount(
          email,
          "unused-test-password-hash",
          UserRole.ADMIN
        )));

      if (
        !account.isEnabled()
          || (!forgedAuthority && account.getRole() != UserRole.ADMIN)
      ) {
        throw new IllegalStateException(
          "Verified admin fixture requires an enabled administrator"
        );
      }

      var principal = new AccountPrincipal(account);
      var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
          principal,
          null,
          List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

      var securityContext = SecurityContextHolder.createEmptyContext();
      securityContext.setAuthentication(authentication);

      var temporarySession = new MockHttpSession();
      AdminMfaSession.markVerified(temporarySession, principal);

      var repository = sessionRepository(
        context.getBean(SessionRepository.class)
      );
      var session = repository.createSession();

      session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        securityContext
      );

      for (String name : Collections.list(
        temporarySession.getAttributeNames()
      )) {
        session.setAttribute(name, temporarySession.getAttribute(name));
      }

      repository.save(session);

      var cookies = new ArrayList<Cookie>();

      if (request.getCookies() != null) {
        Arrays.stream(request.getCookies())
          .filter(cookie -> !cookie.getName().equals("JSESSIONID"))
          .forEach(cookies::add);
      }

      cookies.add(new Cookie("JSESSIONID", session.getId()));
      request.setCookies(cookies.toArray(Cookie[]::new));

      return request;
    };
  }

  @SuppressWarnings("unchecked")
  private static SessionRepository<Session> sessionRepository(
    SessionRepository<?> repository
  ) {
    return (SessionRepository<Session>) repository;
  }
}
