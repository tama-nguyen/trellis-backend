package io.trellis.identity.api.security;

public final class IdentityRateLimitPolicies {
  private IdentityRateLimitPolicies() {}

  public static final String LOGIN = "identity.login";
  public static final String REGISTER = "identity.register";
  public static final String FORGOT = "identity.forgot";
  public static final String RESET = "identity.reset";
}
