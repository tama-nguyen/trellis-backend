package io.trellis.identity.application.view;

import java.util.List;

/** Current user projection shared by native and OIDC-authenticated callers. */
public record AuthenticatedUser(
    String id, String email, String displayName, List<String> permissions) {}
