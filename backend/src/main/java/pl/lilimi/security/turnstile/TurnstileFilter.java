package pl.lilimi.security.turnstile;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TurnstileFilter extends OncePerRequestFilter {

  private final TurnstileVerifier verifier;

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

  public TurnstileFilter(TurnstileVerifier verifier) {
    this.verifier = verifier;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !inquiry.matches(request)
      && !registration.matches(request);
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain chain
  ) throws ServletException, IOException {
    String action = inquiry.matches(request)
      ? "project-inquiry"
      : "registration";

    try {
      verifier.verify(
        request.getHeader("X-Turnstile-Token"),
        action
      );
    } catch (TurnstileRejectedException exception) {
      reject(
        response,
        HttpServletResponse.SC_FORBIDDEN,
        "TURNSTILE_REJECTED"
      );
      return;
    } catch (TurnstileUnavailableException exception) {
      response.setHeader("Retry-After", "30");

      reject(
        response,
        HttpServletResponse.SC_SERVICE_UNAVAILABLE,
        "TURNSTILE_UNAVAILABLE"
      );
      return;
    }

    chain.doFilter(request, response);
  }

  private void reject(
    HttpServletResponse response,
    int status,
    String code
  ) throws IOException {
    response.setStatus(status);
    response.setHeader("Cache-Control", "no-store");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(
      "{\"code\":\"" + code + "\"}"
    );
  }
}
