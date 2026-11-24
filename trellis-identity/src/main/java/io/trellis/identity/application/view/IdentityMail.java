package io.trellis.identity.application.view;

import io.trellis.identity.domain.action.UserActionType;

public record IdentityMail(UserActionType type, String email, String displayName, String secret) {
  @Override
  public String toString() {
    return "IdentityMail[redacted]";
  }
}
