package io.trellis.identity.web.rest.dto.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password-reset email request. Wire responses never reveal whether this email exists. */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 320) String email) {}
