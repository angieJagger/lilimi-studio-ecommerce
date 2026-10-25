package pl.lilimi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.lilimi.auth.application.LoginRateLimiter;

import java.io.IOException;

public class LoginRateLimitFilter extends OncePerRequestFilter {

  private final LoginRateLimiter limiter;
  private final PathPatternRequestMatcher login = PathPatternRequestMatcher.withDefaults()
    .matcher(HttpMethod.POST, "/api/auth/login");

  public LoginRateLimitFilter(LoginRateLimiter limiter) {
    this.limiter = limiter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !login.matches(request);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
    FilterChain chain) throws ServletException, IOException {
    long retryAfter;
    try {
      // The gateway must overwrite forwarding headers; never parse client-supplied
      // X-Forwarded-For here. See deploy/LOGIN_SECURITY.md.
      retryAfter = limiter.reserveAttempt(request.getParameter("email"), request.getRemoteAddr());
    } catch (DataAccessException exception) {
      // Fail closed rather than silently disabling the limiter when storage fails.
      logger.error("Login rate limit storage is unavailable");
      reject(response, 503, 60, "LOGIN_UNAVAILABLE");
      return;
    }
    if (retryAfter > 0) {
      reject(response, 429, retryAfter, "LOGIN_RATE_LIMITED");
      return;
    }
    chain.doFilter(request, response);
  }

  private void reject(HttpServletResponse response, int status, long retryAfter,
    String code) throws IOException {
    response.setStatus(status);
    response.setHeader("Retry-After", Long.toString(retryAfter));
    response.setHeader("Cache-Control", "no-store");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"code\":\"" + code + "\"}");
  }
}
