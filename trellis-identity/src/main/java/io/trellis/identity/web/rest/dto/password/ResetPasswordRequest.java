package io.trellis.identity.web.rest.dto.password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One-time password reset payload. */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(min = 8, max = 128) String newPassword) {}
