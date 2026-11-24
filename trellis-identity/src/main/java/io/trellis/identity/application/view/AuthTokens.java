package io.trellis.identity.application.view;

/** Native opaque access and refresh token pair. */
public record AuthTokens(String accessToken, String refreshToken) {}
