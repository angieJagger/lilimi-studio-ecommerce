package pl.lilimi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.lilimi.auth.application.AccountPrincipal;
import pl.lilimi.auth.persistence.UserAccountRepository;

import java.io.IOException;

public class CredentialsVersionFilter extends OncePerRequestFilter {

  private final UserAccountRepository accounts;

  public CredentialsVersionFilter(UserAccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext()
      .getAuthentication();

    if (
      authentication != null &&
        authentication.isAuthenticated() &&
        authentication.getPrincipal() instanceof AccountPrincipal principal
    ) {
      boolean valid = accounts.findById(principal.getAccountId())
        .filter(account -> account.isEnabled())
        .filter(account ->
          !account.isEmailVerificationRequired() ||
            account.isEmailVerified()
        )
        .filter(account ->
          account.getCredentialsVersion() ==
            principal.getCredentialsVersion()
        )
        .isPresent();

      if (!valid) {
        var session = request.getSession(false);

        if (session != null) {
          session.invalidate();
        }

        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
    }

    filterChain.doFilter(request, response);
  }
}
