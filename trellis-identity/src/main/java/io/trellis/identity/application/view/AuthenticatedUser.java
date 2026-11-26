package io.trellis.identity.application.view;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Current user projection shared by native and OIDC-authenticated callers. */
public record AuthenticatedUser(
    @NotNull String id,
    @NotNull String email,
    @NotNull String displayName,
    @NotNull List<String> permissions) {}
