package io.trellis.app.platform.application.port;

/** Resolves existence for a scope actually used by a resource's authorization decisions. */
public interface GrantResourceLookup {
  String scopeType();

  boolean exists(final String resourceId);
}
