package io.trellis.common.context;

/** Provenance supplied by trusted authentication adapters. */
public enum IdentityOrigin {
  /** Native account and session authentication. */
  NATIVE,
  /** Authentication through an OpenID Connect (OIDC) provider. */
  OIDC
}
