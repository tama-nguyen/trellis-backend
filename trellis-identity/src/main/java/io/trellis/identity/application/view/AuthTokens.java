package io.trellis.identity.application.view;

import jakarta.validation.constraints.NotNull;

/** Native opaque access and refresh token pair. */
public record AuthTokens(@NotNull String accessToken, @NotNull String refreshToken) {}
