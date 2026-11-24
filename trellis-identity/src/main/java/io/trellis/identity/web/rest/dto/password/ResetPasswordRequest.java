package io.trellis.identity.web.rest.dto.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password reset payload using the single-use code sent to the user's email address. */
public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(min = 8, max = 128) String newPassword) {}
