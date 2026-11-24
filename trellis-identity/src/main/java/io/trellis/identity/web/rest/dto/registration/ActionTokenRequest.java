package io.trellis.identity.web.rest.dto.registration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One-time email verification token. */
public record ActionTokenRequest(@NotBlank @Size(max = 128) String token) {}
