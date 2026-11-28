package io.trellis.identity.application.view;

import java.util.UUID;

public record IdentityMailClaim(Long id, UUID lease, String keyVersion, String payload) {
  @Override
  public String toString() {
    return "IdentityMailClaim[id=" + id + "]";
  }
}
