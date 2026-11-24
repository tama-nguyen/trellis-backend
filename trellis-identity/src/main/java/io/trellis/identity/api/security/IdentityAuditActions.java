package io.trellis.identity.api.security;

public final class IdentityAuditActions {
  private IdentityAuditActions() {}

  public static final String AGGREGATE_TYPE = "Identity";
  public static final String LOGIN = "identity.login";
  public static final String REGISTER = "identity.register";
  public static final String VERIFY_EMAIL = "identity.verify-email";
  public static final String REFRESH = "identity.refresh";
  public static final String LOGOUT = "identity.logout";
  public static final String FORGOT_PASSWORD = "identity.forgot-password";
  public static final String RESET_PASSWORD = "identity.reset-password";
}
