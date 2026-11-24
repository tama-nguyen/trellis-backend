package io.trellis.identity.web.rest.dto.authentication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Session revocation payload. */
public record LogoutRequest(@NotBlank @Size(max = 128) String refreshToken) {}
