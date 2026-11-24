package io.trellis.identity.web.rest.dto.authentication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Refresh-token rotation payload. */
public record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {}
