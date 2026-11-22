package pl.lilimi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.lilimi.auth.application.AccountPrincipal;
import pl.lilimi.auth.mfa.AdminMfaSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class AdminMfaFilter extends OncePerRequestFilter {

  private static final Set<String> ALLOWED_REQUESTS = Set.of(
    "GET /api/auth/csrf",
    "GET /api/auth/mfa/status",
    "POST /api/auth/logout",
    "POST /api/auth/mfa/setup",
    "POST /api/auth/mfa/activate",
    "POST /api/auth/mfa/verify",
    "POST /api/auth/mfa/recovery"
  );

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain chain
  ) throws ServletException, IOException {

    var authentication = SecurityContextHolder.getContext()
      .getAuthentication();

    boolean admin = authentication != null
      && authentication.isAuthenticated()
      && authentication.getAuthorities().stream()
      .anyMatch(authority ->
        authority.getAuthority().equals("ROLE_ADMIN")
      );

    String path = request.getRequestURI()
      .substring(request.getContextPath().length());

    if (!admin || !path.startsWith("/api/")) {
      chain.doFilter(request, response);
      return;
    }

    if (ALLOWED_REQUESTS.contains(request.getMethod() + " " + path)) {
      chain.doFilter(request, response);
      return;
    }

    if (
      authentication.getPrincipal() instanceof AccountPrincipal principal
        && AdminMfaSession.isVerified(
        request.getSession(false),
        principal
      )
    ) {
      chain.doFilter(request, response);
      return;
    }

    if (
      request.getMethod().equals("GET")
        && path.equals("/api/auth/session")
    ) {
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
      response.setHeader("Cache-Control", "no-store");
      response.setHeader("X-MFA-Required", "true");
      return;
    }

    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader("Cache-Control", "no-store");
    response.getWriter().write("""
      {"code":"MFA_REQUIRED"}
      """);
  }
}
