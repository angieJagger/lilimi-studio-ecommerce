package pl.lilimi.auth.mfa;

import jakarta.servlet.http.HttpSession;
import pl.lilimi.auth.application.AccountPrincipal;

import java.io.Serializable;
import java.util.UUID;

public final class AdminMfaSession {

  private static final String ATTRIBUTE =
    AdminMfaSession.class.getName() + ".verified";

  private AdminMfaSession() {
  }

  public static boolean isVerified(
    HttpSession session,
    AccountPrincipal principal
  ) {
    return session != null
      && session.getAttribute(ATTRIBUTE) instanceof Verification value
      && value.accountId().equals(principal.getAccountId())
      && value.credentialsVersion() == principal.getCredentialsVersion();
  }

  public static void markVerified(
    HttpSession session,
    AccountPrincipal principal
  ) {
    session.setAttribute(
      ATTRIBUTE,
      new Verification(
        principal.getAccountId(),
        principal.getCredentialsVersion()
      )
    );
  }

  public static void clear(HttpSession session) {
    if (session != null) {
      session.removeAttribute(ATTRIBUTE);
    }
  }

  private record Verification(
    UUID accountId,
    long credentialsVersion
  ) implements Serializable {

    private static final long serialVersionUID = 1L;
  }
}
