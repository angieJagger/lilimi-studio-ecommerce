package pl.lilimi.auth.application;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import pl.lilimi.auth.domain.UserAccount;

import java.util.List;
import java.util.UUID;

public class AccountPrincipal extends User {

  private static final long serialVersionUID = 1L;

  private final UUID accountId;
  private final long credentialsVersion;

  public AccountPrincipal(UserAccount account) {
    super(
      account.getEmail(),
      account.getPasswordHash(),
      account.isEnabled() && (
        !account.isEmailVerificationRequired() ||
          account.isEmailVerified()
      ),
      true,
      true,
      true,
      List.of(new SimpleGrantedAuthority(
        "ROLE_" + account.getRole().name()
      ))
    );

    this.accountId = account.getId();
    this.credentialsVersion = account.getCredentialsVersion();
  }

  public UUID getAccountId() {
    return accountId;
  }

  public long getCredentialsVersion() {
    return credentialsVersion;
  }
}
