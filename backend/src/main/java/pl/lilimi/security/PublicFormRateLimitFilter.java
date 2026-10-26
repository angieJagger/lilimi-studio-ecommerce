package pl.lilimi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.lilimi.security.application.PublicFormRateLimiter;

import java.io.IOException;

public class PublicFormRateLimitFilter
  extends OncePerRequestFilter {

  private final PublicFormRateLimiter limiter;

  private final PathPatternRequestMatcher inquiry =
    PathPatternRequestMatcher.withDefaults().matcher(
      HttpMethod.POST,
      "/api/project-inquiries"
    );

  private final PathPatternRequestMatcher registration =
    PathPatternRequestMatcher.withDefaults().matcher(
      HttpMethod.POST,
      "/api/auth/register"
    );

  public PublicFormRateLimitFilter(
    PublicFormRateLimiter limiter
  ) {
    this.limiter = limiter;
  }

  @Override
  protected boolean shouldNotFilter(
    HttpServletRequest request
  ) {
    return !inquiry.matches(request)
      && !registration.matches(request);
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain chain
  ) throws ServletException, IOException {
    long retryAfter;

    try {
      String source = request.getRemoteAddr();

      retryAfter = inquiry.matches(request)
        ? limiter.reserveInquiry(source)
        : limiter.reserveRegistration(source);
    } catch (DataAccessException exception) {
      logger.error("Public form rate limit storage is unavailable");

      reject(
        response,
        503,
        60,
        "PUBLIC_FORM_UNAVAILABLE"
      );
      return;
    }

    if (retryAfter > 0) {
      reject(
        response,
        429,
        retryAfter,
        "PUBLIC_FORM_RATE_LIMITED"
      );
      return;
    }

    chain.doFilter(request, response);
  }

  private void reject(
    HttpServletResponse response,
    int status,
    long retryAfter,
    String code
  ) throws IOException {
    response.setStatus(status);
    response.setHeader(
      "Retry-After",
      Long.toString(retryAfter)
    );
    response.setHeader("Cache-Control", "no-store");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(
      "{\"code\":\"" + code + "\"}"
    );
  }
}
