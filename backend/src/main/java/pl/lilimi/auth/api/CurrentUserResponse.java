package pl.lilimi.auth.api;

import java.util.List;

public record CurrentUserResponse(
  String email,
  List<String> roles
) {
}
